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
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.ComponentActivity
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.max

class EmulatorActivity : ComponentActivity(), SurfaceHolder.Callback {
    private lateinit var surfaceView: SurfaceView
    private lateinit var status: TextView
    private val running = AtomicBoolean(false)
    @Volatile private var currentInputMask = 0
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
    private external fun nativeSaveState(): String?
    private external fun nativeLoadState(): String?
    private external fun nativeStop()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        romPath = intent.getStringExtra("rom_path").orEmpty()
        if (romPath.isBlank()) {
            finish()
            return
        }
        buildScreen()
    }

    private fun buildScreen() {
        val root = FrameLayout(this).apply { setBackgroundColor(Color.BLACK) }
        surfaceView = SurfaceView(this)
        surfaceView.holder.addCallback(this)
        root.addView(surfaceView, FrameLayout.LayoutParams(-1, -1))

        status = TextView(this).apply {
            text = "Initialisation du moteur NES…"
            setTextColor(Color.WHITE)
            textSize = 13f
            setPadding(12, 8, 12, 8)
            setBackgroundColor(0x99000000.toInt())
        }
        val topBar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(8), dp(4), dp(8), dp(4))
            setBackgroundColor(0x99000000.toInt())
        }
        topBar.addView(status, LinearLayout.LayoutParams(0, -2, 1f))
        val saveStateButton = Button(this).apply {
            text = "SAUVER"
            textSize = 10f
            isAllCaps = false
            setOnClickListener { performStateAction { nativeSaveState() } }
        }
        val loadStateButton = Button(this).apply {
            text = "CHARGER"
            textSize = 10f
            isAllCaps = false
            setOnClickListener { performStateAction { nativeLoadState() } }
        }
        topBar.addView(saveStateButton, LinearLayout.LayoutParams(-2, dp(42)))
        topBar.addView(loadStateButton, LinearLayout.LayoutParams(-2, dp(42)))
        root.addView(topBar, FrameLayout.LayoutParams(-1, -2, Gravity.TOP))

        val controls = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(18, 8, 18, 8)
            setBackgroundColor(0x66000000)
        }
        val dpad = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER }
        val horizontal = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER }
        addControl(horizontal, "◀", LEFT)
        addControl(horizontal, "▶", RIGHT)
        addControl(dpad, "▲", UP)
        dpad.addView(horizontal)
        addControl(dpad, "▼", DOWN)
        controls.addView(dpad)
        val spacer = View(this)
        controls.addView(spacer, LinearLayout.LayoutParams(0, 1, 1f))
        val system = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER }
        val systemRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER }
        addControl(systemRow, "SELECT", SELECT)
        addControl(systemRow, "START", START)
        system.addView(systemRow)
        controls.addView(system)
        val spacer2 = View(this)
        controls.addView(spacer2, LinearLayout.LayoutParams(0, 1, 1f))
        val buttons = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER }
        val buttonTop = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER }
        val buttonBottom = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER }
        addControl(buttonTop, "Y", Y)
        addControl(buttonTop, "X", X)
        addControl(buttonBottom, "B", B)
        addControl(buttonBottom, "A", A)
        buttons.addView(buttonTop)
        buttons.addView(buttonBottom)
        controls.addView(buttons)
        root.addView(controls, FrameLayout.LayoutParams(-1, dp(116), Gravity.BOTTOM))
        setContentView(root)
    }

    private fun addControl(parent: LinearLayout, label: String, bit: Int) {
        val button = Button(this).apply {
            text = label
            textSize = if (label.length > 1) 10f else 18f
            isAllCaps = false
            setTextColor(Color.WHITE)
            setBackgroundColor(0xCC26384A.toInt())
            setPadding(8, 0, 8, 0)
            minWidth = dp(if (label.length > 1) 68 else 48)
            minHeight = dp(42)
            setOnTouchListener { _, event ->
                when (event.actionMasked) {
                    MotionEvent.ACTION_DOWN, MotionEvent.ACTION_POINTER_DOWN -> updateInput(bit, true)
                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL, MotionEvent.ACTION_POINTER_UP -> updateInput(bit, false)
                }
                true
            }
        }
        parent.addView(button, LinearLayout.LayoutParams(-2, dp(46)).apply {
            setMargins(dp(3), dp(2), dp(3), dp(2))
        })
    }

    private fun updateInput(bit: Int, pressed: Boolean) {
        currentInputMask = if (pressed) currentInputMask or bit else currentInputMask and bit.inv()
        nativeSetInputMask(currentInputMask)
    }

    override fun surfaceCreated(holder: SurfaceHolder) {
        val saveDirectory = java.io.File(filesDir, "saves").apply { mkdirs() }
        val error = nativeStart(holder.surface, romPath, filesDir.absolutePath, saveDirectory.absolutePath)
        if (error != null) {
            nativeStop()
            status.text = error
            return
        }
        nativeStarted = true
        status.text = "Nestopia actif · NES"
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

    private fun performStateAction(action: () -> String?) {
        if (!nativeStarted) return
        stopLoop()
        val error = action()
        status.text = error ?: "Sauvegarde d’état mise à jour"
        startAudioAndLoop()
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

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        val bit = keyMask(keyCode)
        if (bit != 0) { updateInput(bit, true); return true }
        return super.onKeyDown(keyCode, event)
    }

    override fun onKeyUp(keyCode: Int, event: KeyEvent?): Boolean {
        val bit = keyMask(keyCode)
        if (bit != 0) { updateInput(bit, false); return true }
        return super.onKeyUp(keyCode, event)
    }

    override fun onPause() {
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
