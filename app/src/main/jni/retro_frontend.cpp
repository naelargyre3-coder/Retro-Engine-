#include <jni.h>
#include <android/native_window.h>
#include <android/native_window_jni.h>
#include <android/log.h>
#include <dlfcn.h>
#include <algorithm>
#include <cmath>
#include <cstdint>
#include <cstdio>
#include <atomic>
#include <cstring>
#include <deque>
#include <mutex>
#include <string>
#include <sys/stat.h>
#include <sstream>
#include <iomanip>
#include <vector>
#include "libretro_minimal.h"

#define LOG_TAG "RetroEngine"
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

namespace {
struct CoreApi {
    void *handle = nullptr;
    decltype(&retro_set_environment) set_environment = nullptr;
    decltype(&retro_set_video_refresh) set_video_refresh = nullptr;
    decltype(&retro_set_audio_sample) set_audio_sample = nullptr;
    decltype(&retro_set_audio_sample_batch) set_audio_sample_batch = nullptr;
    decltype(&retro_set_input_poll) set_input_poll = nullptr;
    decltype(&retro_set_input_state) set_input_state = nullptr;
    decltype(&retro_set_controller_port_device) set_controller_port_device = nullptr;
    decltype(&retro_init) init = nullptr;
    decltype(&retro_deinit) deinit = nullptr;
    decltype(&retro_get_system_info) get_system_info = nullptr;
    decltype(&retro_get_system_av_info) get_av_info = nullptr;
    decltype(&retro_load_game) load_game = nullptr;
    decltype(&retro_unload_game) unload_game = nullptr;
    decltype(&retro_run) run = nullptr;
    decltype(&retro_get_memory_data) get_memory_data = nullptr;
    decltype(&retro_get_memory_size) get_memory_size = nullptr;
} core;

ANativeWindow *window = nullptr;
std::vector<uint8_t> romBytes;
std::string currentRomPath;
std::string currentSavePath;
std::string systemDirectory;
std::string saveDirectory;
std::string lastError;
std::mutex audioMutex;
std::deque<int16_t> audioQueue;
std::atomic<int> inputMask{0};
std::atomic<int> pixelFormat{RETRO_PIXEL_FORMAT_RGB565};
double fps = 60.0;
double sampleRate = 32040.0;
bool initialized = false;
bool gameLoaded = false;

template <typename T>
bool loadSymbol(T &target, const char *name) {
    target = reinterpret_cast<T>(dlsym(core.handle, name));
    if (!target) {
        lastError = std::string("Symbole libretro manquant : ") + name;
        return false;
    }
    return true;
}

bool environment(unsigned command, void *data) {
    switch (command) {
        case RETRO_ENVIRONMENT_GET_CAN_DUPE:
            if (data) *static_cast<bool *>(data) = true;
            return true;
        case RETRO_ENVIRONMENT_GET_SYSTEM_DIRECTORY:
            if (data) *static_cast<const char **>(data) = systemDirectory.c_str();
            return true;
        case RETRO_ENVIRONMENT_GET_SAVE_DIRECTORY:
            if (data) *static_cast<const char **>(data) = saveDirectory.c_str();
            return true;
        case RETRO_ENVIRONMENT_SET_PIXEL_FORMAT:
            if (!data) return false;
            pixelFormat.store(*static_cast<const int *>(data));
            return true;
        case RETRO_ENVIRONMENT_SET_SUPPORT_NO_GAME:
            if (data) *static_cast<bool *>(data) = false;
            return true;
        case RETRO_ENVIRONMENT_SET_INPUT_DESCRIPTORS:
        case RETRO_ENVIRONMENT_SET_MESSAGE:
        case RETRO_ENVIRONMENT_SET_GEOMETRY:
        case RETRO_ENVIRONMENT_SET_SYSTEM_AV_INFO:
            return true;
        case RETRO_ENVIRONMENT_GET_LANGUAGE:
            if (data) *static_cast<unsigned *>(data) = 2; // French
            return true;
        default:
            return false;
    }
}

void inputPoll() {}

int16_t inputState(unsigned port, unsigned device, unsigned index, unsigned id) {
    if (port != 0 || device != RETRO_DEVICE_JOYPAD || index != 0) return 0;
    const int mask = inputMask.load();
    if (id == RETRO_DEVICE_ID_JOYPAD_MASK) return static_cast<int16_t>(mask);
    if (id > 15) return 0;
    return (mask & (1 << id)) ? 1 : 0;
}

void audioSample(int16_t left, int16_t right) {
    std::lock_guard<std::mutex> lock(audioMutex);
    if (audioQueue.size() > 32768) {
        audioQueue.pop_front();
        audioQueue.pop_front();
    }
    audioQueue.push_back(left);
    audioQueue.push_back(right);
}

size_t audioBatch(const int16_t *data, size_t frames) {
    if (!data || frames == 0) return 0;
    std::lock_guard<std::mutex> lock(audioMutex);
    const size_t samples = frames * 2;
    const size_t maxQueue = 65536;
    if (samples >= maxQueue) {
        audioQueue.clear();
        data += samples - maxQueue;
        frames = maxQueue / 2;
    } else {
        while (audioQueue.size() + samples > maxQueue) {
            audioQueue.pop_front();
            if (!audioQueue.empty()) audioQueue.pop_front();
        }
    }
    audioQueue.insert(audioQueue.end(), data, data + frames * 2);
    return frames;
}

void drawFrame(const void *data, unsigned width, unsigned height, size_t pitch) {
    if (!data || width == 0 || height == 0 || !window) return;
    ANativeWindow_Buffer buffer{};
    if (ANativeWindow_lock(window, &buffer, nullptr) != 0) return;

    const float scale = std::min(
        static_cast<float>(buffer.width) / static_cast<float>(width),
        static_cast<float>(buffer.height) / static_cast<float>(height));
    const int drawWidth = std::max(1, static_cast<int>(width * scale));
    const int drawHeight = std::max(1, static_cast<int>(height * scale));
    const int offsetX = (buffer.width - drawWidth) / 2;
    const int offsetY = (buffer.height - drawHeight) / 2;
    auto *dst = static_cast<uint16_t *>(buffer.bits);
    for (int y = 0; y < buffer.height; ++y) {
        auto *row = dst + y * buffer.stride;
        std::fill(row, row + buffer.width, static_cast<uint16_t>(0));
    }

    const int format = pixelFormat.load();
    const auto *source = static_cast<const uint8_t *>(data);
    for (int y = 0; y < drawHeight; ++y) {
        const unsigned sourceY = std::min(height - 1, static_cast<unsigned>(y / scale));
        auto *row = dst + (offsetY + y) * buffer.stride;
        for (int x = 0; x < drawWidth; ++x) {
            const unsigned sourceX = std::min(width - 1, static_cast<unsigned>(x / scale));
            uint8_t r = 0, g = 0, b = 0;
            const uint8_t *p = source + sourceY * pitch;
            if (format == RETRO_PIXEL_FORMAT_XRGB8888) {
                uint32_t raw;
                std::memcpy(&raw, p + sourceX * 4, sizeof(raw));
                r = static_cast<uint8_t>((raw >> 16) & 0xff);
                g = static_cast<uint8_t>((raw >> 8) & 0xff);
                b = static_cast<uint8_t>(raw & 0xff);
            } else if (format == RETRO_PIXEL_FORMAT_RGB565) {
                uint16_t raw;
                std::memcpy(&raw, p + sourceX * 2, sizeof(raw));
                r = static_cast<uint8_t>(((raw >> 11) & 0x1F) * 255 / 31);
                g = static_cast<uint8_t>(((raw >> 5) & 0x3F) * 255 / 63);
                b = static_cast<uint8_t>((raw & 0x1F) * 255 / 31);
            } else {
                uint16_t raw;
                std::memcpy(&raw, p + sourceX * 2, sizeof(raw));
                r = static_cast<uint8_t>(((raw >> 10) & 0x1F) * 255 / 31);
                g = static_cast<uint8_t>(((raw >> 5) & 0x1F) * 255 / 31);
                b = static_cast<uint8_t>((raw & 0x1F) * 255 / 31);
            }
            row[offsetX + x] = static_cast<uint16_t>(((r >> 3) << 11) | ((g >> 2) << 5) | (b >> 3));
        }
    }
    ANativeWindow_unlockAndPost(window);
}

std::string makeSavePath(const std::vector<uint8_t> &bytes) {
    uint64_t hash = 14695981039346656037ull;
    for (uint8_t byte : bytes) {
        hash ^= byte;
        hash *= 1099511628211ull;
    }
    std::ostringstream name;
    name << saveDirectory << "/nes-" << std::hex << std::setw(16) << std::setfill('0') << hash << ".srm";
    return name.str();
}

void loadSaveRam() {
    if (!gameLoaded || !core.get_memory_data || !core.get_memory_size) return;
    void *memory = core.get_memory_data(RETRO_MEMORY_SAVE_RAM);
    const size_t size = core.get_memory_size(RETRO_MEMORY_SAVE_RAM);
    if (!memory || size == 0 || currentSavePath.empty()) return;
    FILE *file = fopen(currentSavePath.c_str(), "rb");
    if (!file) return;
    const size_t read = fread(memory, 1, size, file);
    fclose(file);
    if (read != size) LOGE("SRAM existante incomplète : %zu/%zu octets", read, size);
}

void saveSaveRam() {
    if (!gameLoaded || !core.get_memory_data || !core.get_memory_size || currentSavePath.empty()) return;
    void *memory = core.get_memory_data(RETRO_MEMORY_SAVE_RAM);
    const size_t size = core.get_memory_size(RETRO_MEMORY_SAVE_RAM);
    if (!memory || size == 0) return;
    const std::string temporary = currentSavePath + ".tmp";
    FILE *file = fopen(temporary.c_str(), "wb");
    if (!file) {
        LOGE("Impossible d'ouvrir le fichier SRAM temporaire.");
        return;
    }
    const size_t written = fwrite(memory, 1, size, file);
    const bool flushed = fflush(file) == 0;
    fclose(file);
    if (written != size || !flushed || rename(temporary.c_str(), currentSavePath.c_str()) != 0) {
        remove(temporary.c_str());
        LOGE("Échec de l'écriture de la sauvegarde SRAM.");
    }
}

bool openCore() {
    if (core.handle) return true;
    core.handle = dlopen("libnestopia_libretro.so", RTLD_NOW);
    if (!core.handle) {
        const char *reason = dlerror();
        lastError = std::string("Chargement du cœur NES impossible : ") + (reason ? reason : "bibliothèque absente");
        return false;
    }
#define LOAD(name) if (!loadSymbol(core.name, "retro_" #name)) return false
    LOAD(set_environment);
    LOAD(set_video_refresh);
    LOAD(set_audio_sample);
    LOAD(set_audio_sample_batch);
    LOAD(set_input_poll);
    LOAD(set_input_state);
    LOAD(set_controller_port_device);
    LOAD(init);
    LOAD(deinit);
    LOAD(get_system_info);
    LOAD(get_av_info);
    LOAD(load_game);
    LOAD(unload_game);
    LOAD(run);
    LOAD(get_memory_data);
    LOAD(get_memory_size);
#undef LOAD
    core.set_environment(environment);
    core.set_video_refresh(drawFrame);
    core.set_audio_sample(audioSample);
    core.set_audio_sample_batch(audioBatch);
    core.set_input_poll(inputPoll);
    core.set_input_state(inputState);
    core.init();
    initialized = true;

    retro_system_info info{};
    core.get_system_info(&info);
    if (info.valid_extensions && std::string(info.valid_extensions).find("nes") == std::string::npos) {
        lastError = "Le cœur chargé n'annonce pas le format NES attendu.";
        return false;
    }
    retro_system_av_info av{};
    core.get_av_info(&av);
    if (av.timing.fps > 1.0 && av.timing.fps < 240.0) fps = av.timing.fps;
    if (av.timing.sample_rate > 8000.0 && av.timing.sample_rate < 192000.0) sampleRate = av.timing.sample_rate;
    return true;
}
}

extern "C" JNIEXPORT jstring JNICALL
Java_fr_retroengine_EmulatorActivity_nativeStart(JNIEnv *env, jobject, jobject surface,
                                                  jstring romPath, jstring systemDir, jstring saveDir) {
    lastError.clear();
    const char *sys = env->GetStringUTFChars(systemDir, nullptr);
    const char *save = env->GetStringUTFChars(saveDir, nullptr);
    systemDirectory = sys ? sys : "";
    saveDirectory = save ? save : "";
    if (sys) env->ReleaseStringUTFChars(systemDir, sys);
    if (save) env->ReleaseStringUTFChars(saveDir, save);

    if (window) ANativeWindow_release(window);
    window = ANativeWindow_fromSurface(env, surface);
    if (!window) lastError = "Surface Android indisponible.";
    else ANativeWindow_setBuffersGeometry(window, 0, 0, WINDOW_FORMAT_RGB_565);
    if (!lastError.empty()) return env->NewStringUTF(lastError.c_str());

    const char *path = env->GetStringUTFChars(romPath, nullptr);
    if (!path) return env->NewStringUTF("Chemin de ROM invalide.");
    FILE *file = fopen(path, "rb");
    if (!file) {
        env->ReleaseStringUTFChars(romPath, path);
        return env->NewStringUTF("Impossible de lire la ROM importée.");
    }
    fseek(file, 0, SEEK_END);
    const long fileSize = ftell(file);
    rewind(file);
    if (fileSize <= 16 || fileSize > 64 * 1024 * 1024) {
        fclose(file);
        env->ReleaseStringUTFChars(romPath, path);
        return env->NewStringUTF("Taille de ROM NES invalide ou non prise en charge.");
    }
    romBytes.resize(static_cast<size_t>(fileSize));
    const size_t read = fread(romBytes.data(), 1, romBytes.size(), file);
    fclose(file);
    currentRomPath = path;
    env->ReleaseStringUTFChars(romPath, path);
    if (read != romBytes.size()) return env->NewStringUTF("Lecture incomplète de la ROM.");

    if (!openCore()) return env->NewStringUTF(lastError.c_str());
    retro_system_info systemInfo{};
    core.get_system_info(&systemInfo);
    retro_game_info game{};
    game.path = currentRomPath.c_str();
    game.data = systemInfo.need_fullpath ? nullptr : romBytes.data();
    game.size = systemInfo.need_fullpath ? 0 : romBytes.size();
    if (!core.load_game(&game)) {
        lastError = "Nestopia a refusé cette ROM. Vérifie son format et son intégrité.";
        return env->NewStringUTF(lastError.c_str());
    }
    gameLoaded = true;
    mkdir(saveDirectory.c_str(), 0700);
    currentSavePath = makeSavePath(romBytes);
    loadSaveRam();
    core.set_controller_port_device(0, RETRO_DEVICE_JOYPAD);
    {
        std::lock_guard<std::mutex> lock(audioMutex);
        audioQueue.clear();
    }
    return nullptr;
}

extern "C" JNIEXPORT void JNICALL
Java_fr_retroengine_EmulatorActivity_nativeSetSurface(JNIEnv *env, jobject, jobject surface) {
    ANativeWindow *next = surface ? ANativeWindow_fromSurface(env, surface) : nullptr;
    if (window) ANativeWindow_release(window);
    window = next;
    if (window) ANativeWindow_setBuffersGeometry(window, 0, 0, WINDOW_FORMAT_RGB_565);
}

extern "C" JNIEXPORT void JNICALL
Java_fr_retroengine_EmulatorActivity_nativeRunFrame(JNIEnv *, jobject) {
    if (gameLoaded && core.run) core.run();
}

extern "C" JNIEXPORT void JNICALL
Java_fr_retroengine_EmulatorActivity_nativeSetInputMask(JNIEnv *, jobject, jint mask) {
    inputMask.store(mask);
}

extern "C" JNIEXPORT jint JNICALL
Java_fr_retroengine_EmulatorActivity_nativeDrainAudio(JNIEnv *env, jobject, jshortArray output) {
    if (!output) return 0;
    const jsize capacity = env->GetArrayLength(output);
    std::vector<jshort> samples;
    {
        std::lock_guard<std::mutex> lock(audioMutex);
        const size_t count = std::min(static_cast<size_t>(capacity), audioQueue.size());
        samples.reserve(count);
        for (size_t i = 0; i < count; ++i) {
            samples.push_back(audioQueue.front());
            audioQueue.pop_front();
        }
    }
    if (!samples.empty()) env->SetShortArrayRegion(output, 0, static_cast<jsize>(samples.size()), samples.data());
    return static_cast<jint>(samples.size());
}

extern "C" JNIEXPORT jdouble JNICALL
Java_fr_retroengine_EmulatorActivity_nativeGetSampleRate(JNIEnv *, jobject) {
    return sampleRate;
}

extern "C" JNIEXPORT jdouble JNICALL
Java_fr_retroengine_EmulatorActivity_nativeGetFps(JNIEnv *, jobject) {
    return fps;
}

extern "C" JNIEXPORT void JNICALL
Java_fr_retroengine_EmulatorActivity_nativeSaveRam(JNIEnv *, jobject) {
    saveSaveRam();
}

extern "C" JNIEXPORT void JNICALL
Java_fr_retroengine_EmulatorActivity_nativeStop(JNIEnv *, jobject) {
    saveSaveRam();
    if (gameLoaded && core.unload_game) core.unload_game();
    gameLoaded = false;
    if (initialized && core.deinit) core.deinit();
    initialized = false;
    if (core.handle) dlclose(core.handle);
    core = CoreApi{};
    if (window) ANativeWindow_release(window);
    window = nullptr;
    romBytes.clear();
    currentRomPath.clear();
    currentSavePath.clear();
    {
        std::lock_guard<std::mutex> lock(audioMutex);
        audioQueue.clear();
    }
}
