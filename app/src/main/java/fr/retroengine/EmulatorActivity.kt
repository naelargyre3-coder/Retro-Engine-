package fr.retroengine

import android.content.pm.ActivityInfo
import android.graphics.Color
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.os.Bundle
import android.view.Gravity
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.Surface
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.max

class EmulatorActivity : ComponentActivity(), SurfaceHolder.Callback {
    private lateinit var surfaceView: SurfaceView
    private val running = AtomicBoolean(false)
    @Volatile private var currentInputMask = 0
    @Volatile private var touchInputMask = 0
    @Volatile private var hardwareInputMask = 0
    private val pressedHardwareKeys = mutableSetOf<Int>()
    private var renderThread: Thread? = null
    private var audioTrack: AudioTrack? = null
    private var nativeStarted = false
    private var romPath: String = ""

    companion object {
        init {
            System.loadLibrary("retro_frontend")
        }
        private const val B = 1 shl 0
        private const val Y = 1 shl 1
        private const val SELECT = 1 shl 2
        private const val START = 1 shl 3
        private const val UP = 1 shl 4
        private const val DOWN = 1 shl 5
        private const val LEFT = 1 shl 6
        private const val RIGHT = 1 shl 7
        private const val A = 1 shl 8
        private const val X = 1 shl 9
    }

    private external fun nativeStart(surface: Surface, romPath: String, systemDir: String, saveDir: String): String?
    private external fun nativeSetSurface(surface: Surface?)
    private external fun nativeRunFrame()
    private external fun nativeSetInputMask(mask: Int)
    private external fun nativeDrainAudio(output: ShortArray): Int
    private external fun nativeGetSampleRate(): Double
    private external fun nativeGetFps(): Double
    private external fun nativeSaveRam()
    private external fun nativeStop()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        romPath = intent.getStringExtra("rom_path").orEmpty()
        if (romPath.isBlank()) {
            finish()
            return
        }
        enterImmersiveMode()
        buildScreen()
    }

    private fun buildScreen() {
        val root = FrameLayout(this).apply { setBackgroundColor(Color.BLACK) }
        surfaceView = SurfaceView(this)
        surfaceView.holder.addCallback(this)
        root.addView(surfaceView, FrameLayout.LayoutParams(-1, -1))

        // Barre supérieure épurée : uniquement SELECT et START, centrés.
        val topBar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(dp(8), dp(4), dp(8), dp(4))
            setBackgroundColor(0x66000000)
        }
        addControl(topBar, "SELECT", SELECT, 76, 42)
        addControl(topBar, "START", START, 76, 42)
        root.addView(topBar, FrameLayout.LayoutParams(-1, dp(52), Gravity.TOP))

        val controls = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(18), dp(6), dp(18), dp(6))
            setBackgroundColor(0x66000000)
        }

        // Croix directionnelle symétrique : les quatre directions ont exactement
        // les mêmes dimensions et des zones tactiles identiques.
        val dpad = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
        }
        val dpadTop = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
        }
        addControl(dpadTop, "▲", UP, 50, 50)
        dpad.addView(dpadTop)

        val dpadMiddle = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
        }
        addControl(dpadMiddle, "◀", LEFT, 50, 50)
        dpadMiddle.addView(View(this), LinearLayout.LayoutParams(dp(54), dp(54)))
        addControl(dpadMiddle, "▶", RIGHT, 50, 50)
        dpad.addView(dpadMiddle)

        val dpadBottom = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
        }
        addControl(dpadBottom, "▼", DOWN, 50, 50)
        dpad.addView(dpadBottom)
        controls.addView(dpad, LinearLayout.LayoutParams(dp(162), dp(162)))

        controls.addView(View(this), LinearLayout.LayoutParams(0, 1, 1f))

        // Commandes d'action NES uniquement : B et A, de taille identique.
        val actionButtons = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
        }
        addControl(actionButtons, "B", B, 58, 58)
        addControl(actionButtons, "A", A, 58, 58)
        controls.addView(actionButtons)
        root.addView(controls, FrameLayout.LayoutParams(-1, dp(180), Gravity.BOTTOM))
        setContentView(root)
    }

    private fun addControl(
        parent: LinearLayout,
        label: String,
        bit: Int,
        widthDp: Int,
        heightDp: Int
    ) {
        val button = TextView(this).apply {
            text = label
            gravity = Gravity.CENTER
            textSize = if (label.length > 1) 10f else 19f
            setTextColor(Color.WHITE)
            setBackgroundColor(0xCC26384A.toInt())
            isClickable = true
            isFocusable = true
            setOnTouchListener { _, event ->
                when (event.actionMasked) {
                    MotionEvent.ACTION_DOWN -> updateInput(bit, true)
                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> updateInput(bit, false)
                }
                true
            }
        }
        parent.addView(button, LinearLayout.LayoutParams(dp(widthDp), dp(heightDp)).apply {
            setMargins(dp(2), dp(2), dp(2), dp(2))
        })
    }

    private fun updateInput(bit: Int, pressed: Boolean) {
        touchInputMask = if (pressed) touchInputMask or bit else touchInputMask and bit.inv()
        publishInputMask()
    }

    override fun surfaceCreated(holder: SurfaceHolder) {
        val saveDirectory = java.io.File(filesDir, "saves").apply { mkdirs() }
        val error = nativeStart(holder.surface, romPath, filesDir.absolutePath, saveDirectory.absolutePath)
        if (error != null) {
            nativeStop()
            android.util.Log.e("RetroEngine", error)
            return
        }
        nativeStarted = true
        startAudioAndLoop()
    }

    override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
        if (nativeStarted) nativeSetSurface(holder.surface)
    }

    override fun surfaceDestroyed(holder: SurfaceHolder) {
        stopLoop()
        if (nativeStarted) {
            nativeSetSurface(null)
            nativeStop()
            nativeStarted = false
        }
    }

    private fun startAudioAndLoop() {
        val rate = nativeGetSampleRate().toInt().coerceIn(22050, 96000)
        val minBytes = AudioTrack.getMinBufferSize(rate, AudioFormat.CHANNEL_OUT_STEREO, AudioFormat.ENCODING_PCM_16BIT)
        val bufferBytes = max(minBytes, rate / 5 * 4)
        audioTrack = AudioTrack.Builder()
            .setAudioAttributes(AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_GAME)
                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                .build())
            .setAudioFormat(AudioFormat.Builder()
                .setSampleRate(rate)
                .setChannelMask(AudioFormat.CHANNEL_OUT_STEREO)
                .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                .build())
            .setBufferSizeInBytes(bufferBytes)
            .setTransferMode(AudioTrack.MODE_STREAM)
            .build()
        audioTrack?.play()
        running.set(true)
        val frameRate = nativeGetFps().coerceIn(30.0, 120.0)
        val frameNanos = (1_000_000_000.0 / frameRate).toLong()
        renderThread = Thread({
            val samples = ShortArray(4096)
            var nextFrame = System.nanoTime()
            while (running.get()) {
                nativeSetInputMask(currentInputMask)
                nativeRunFrame()
                val count = nativeDrainAudio(samples)
                if (count > 0) audioTrack?.write(samples, 0, count)
                nextFrame += frameNanos
                val wait = nextFrame - System.nanoTime()
                if (wait > 0) {
                    try {
                        Thread.sleep(wait / 1_000_000, (wait % 1_000_000).toInt())
                    } catch (_: InterruptedException) {
                        break
                    }
                } else if (wait < -frameNanos * 4) {
                    nextFrame = System.nanoTime()
                }
            }
        }, "RetroEngine-NES").apply { start() }
    }

    private fun stopLoop() {
        running.set(false)
        renderThread?.interrupt()
        try { renderThread?.join(500) } catch (_: InterruptedException) { }
        renderThread = null
        audioTrack?.pause()
        audioTrack?.flush()
        audioTrack?.release()
        audioTrack = null
    }

    private fun keyMask(keyCode: Int): Int = when (keyCode) {
        KeyEvent.KEYCODE_DPAD_UP -> UP
        KeyEvent.KEYCODE_DPAD_DOWN -> DOWN
        KeyEvent.KEYCODE_DPAD_LEFT -> LEFT
        KeyEvent.KEYCODE_DPAD_RIGHT -> RIGHT
        KeyEvent.KEYCODE_BUTTON_A, KeyEvent.KEYCODE_Z -> A
        KeyEvent.KEYCODE_BUTTON_B, KeyEvent.KEYCODE_X -> B
        KeyEvent.KEYCODE_BUTTON_X -> X
        KeyEvent.KEYCODE_BUTTON_Y -> Y
        KeyEvent.KEYCODE_BUTTON_START, KeyEvent.KEYCODE_ENTER -> START
        KeyEvent.KEYCODE_BUTTON_SELECT -> SELECT
        else -> 0
    }

    private fun publishInputMask() {
        currentInputMask = touchInputMask or hardwareInputMask
        nativeSetInputMask(currentInputMask)
    }

    private fun refreshHardwareInputMask() {
        var mask = 0
        for (keyCode in pressedHardwareKeys) mask = mask or keyMask(keyCode)
        hardwareInputMask = mask
        publishInputMask()
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (keyMask(keyCode) != 0) {
            pressedHardwareKeys.add(keyCode)
            refreshHardwareInputMask()
            return true
        }
        return super.onKeyDown(keyCode, event)
    }

    override fun onKeyUp(keyCode: Int, event: KeyEvent?): Boolean {
        if (keyMask(keyCode) != 0) {
            pressedHardwareKeys.remove(keyCode)
            refreshHardwareInputMask()
            return true
        }
        return super.onKeyUp(keyCode, event)
    }

    override fun onGenericMotionEvent(event: MotionEvent): Boolean {
        if (event.source and android.view.InputDevice.SOURCE_JOYSTICK == android.view.InputDevice.SOURCE_JOYSTICK && event.action == MotionEvent.ACTION_MOVE) {
            var mask = 0
            val x = event.getAxisValue(MotionEvent.AXIS_X)
            val y = event.getAxisValue(MotionEvent.AXIS_Y)
            val hatX = event.getAxisValue(MotionEvent.AXIS_HAT_X)
            val hatY = event.getAxisValue(MotionEvent.AXIS_HAT_Y)
            if (x < -0.5f || hatX < -0.5f) mask = mask or LEFT
            if (x > 0.5f || hatX > 0.5f) mask = mask or RIGHT
            if (y < -0.5f || hatY < -0.5f) mask = mask or UP
            if (y > 0.5f || hatY > 0.5f) mask = mask or DOWN
            var keyBits = 0
            for (keyCode in pressedHardwareKeys) keyBits = keyBits or keyMask(keyCode)
            hardwareInputMask = keyBits or mask
            publishInputMask()
            return true
        }
        return super.onGenericMotionEvent(event)
    }

    private fun enterImmersiveMode() {
        WindowInsetsControllerCompat(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) enterImmersiveMode()
    }

    override fun onPause() {
        touchInputMask = 0
        hardwareInputMask = 0
        pressedHardwareKeys.clear()
        currentInputMask = 0
        if (nativeStarted) nativeSetInputMask(0)
        stopLoop()
        if (nativeStarted) nativeSaveRam()
        super.onPause()
    }

    override fun onResume() {
        super.onResume()
        if (nativeStarted && !running.get()) startAudioAndLoop()
    }

    override fun onDestroy() {
        stopLoop()
        if (nativeStarted) {
            nativeStop()
            nativeStarted = false
        }
        super.onDestroy()
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()
}
