package com.retroengine.app

import android.content.Context
import android.graphics.drawable.GradientDrawable
import android.hardware.input.InputManager
import android.os.Bundle
import android.view.Gravity
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.swordfish.libretrodroid.GLRetroView
import com.swordfish.libretrodroid.GLRetroViewData
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File
import kotlin.math.abs

class GameActivity : ComponentActivity() {

    private var retroView: GLRetroView? = null
    private var touchOverlay: View? = null
    private var overlayHidden = false
    private var manualHidden = false
    private var sramFile: File? = null
    private var lastSram: ByteArray? = null
    private var inputManager: InputManager? = null
    private var mapping: ControllerMapping? = null
    private var embedded: EmbeddedConfig? = null
    private var console: GameConsole = GameConsole.NES

    private val dirState = BooleanArray(4)
    private val dirKeys = intArrayOf(
        KeyEvent.KEYCODE_DPAD_LEFT,
        KeyEvent.KEYCODE_DPAD_RIGHT,
        KeyEvent.KEYCODE_DPAD_UP,
        KeyEvent.KEYCODE_DPAD_DOWN
    )

    private val deviceListener = object : InputManager.InputDeviceListener {
        override fun onInputDeviceAdded(deviceId: Int) = updateOverlay()
        override fun onInputDeviceRemoved(deviceId: Int) = updateOverlay()
        override fun onInputDeviceChanged(deviceId: Int) = updateOverlay()
    }

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val cfg = if (intent.getBooleanExtra("embedded", false)) EmbeddedGame.load(this) else null
        embedded = cfg
        if (cfg != null) ControlAdjust.importEmbedded(this)

        val gameId: String?
        val rom: File?
        if (cfg != null) {
            gameId = cfg.id
            rom = try {
                EmbeddedGame.romFile(this, cfg)
            } catch (e: Exception) {
                null
            }
        } else {
            gameId = intent.getStringExtra("gameId")
            rom = if (gameId != null) GameLibrary(this).romFile(gameId) else null
        }

        if (gameId == null || rom == null || !rom.exists()) {
            Toast.makeText(this, "ROM introuvable.", Toast.LENGTH_LONG).show()
            finish()
            return
        }
        startGame(gameId, rom)
    }

    override fun onDestroy() {
        inputManager?.unregisterInputDeviceListener(deviceListener)
        super.onDestroy()
        if (isFinishing) {
            android.os.Process.killProcess(android.os.Process.myPid())
        }
    }

    // ---------- Manettes ----------

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        val view = retroView
        val map = mapping
        if (view != null && map != null && isGamepadSource(event.source)) {
            val action = map.lookup(event.keyCode)
            if (action != null) {
                if (event.repeatCount == 0 &&
                    (event.action == KeyEvent.ACTION_DOWN || event.action == KeyEvent.ACTION_UP)
                ) {
                    view.sendKeyEvent(event.action, action.retroKey, 0)
                }
                return true
            }
        }
        return super.dispatchKeyEvent(event)
    }

    private fun setDirection(index: Int, on: Boolean) {
        if (dirState[index] == on) return
        dirState[index] = on
        val action = if (on) KeyEvent.ACTION_DOWN else KeyEvent.ACTION_UP
        retroView?.sendKeyEvent(action, dirKeys[index], 0)
    }

    private fun pickAxis(e: MotionEvent, first: Int, second: Int): Float {
        val v = e.getAxisValue(first)
        return if (abs(v) > 0.01f) v else e.getAxisValue(second)
    }

    override fun dispatchGenericMotionEvent(event: MotionEvent): Boolean {
        val view = retroView
        if (view != null && isGamepadSource(event.source) &&
            event.actionMasked == MotionEvent.ACTION_MOVE
        ) {
            val x = event.getAxisValue(MotionEvent.AXIS_X)
            val y = event.getAxisValue(MotionEvent.AXIS_Y)
            val hatX = event.getAxisValue(MotionEvent.AXIS_HAT_X)
            val hatY = event.getAxisValue(MotionEvent.AXIS_HAT_Y)
            if (console == GameConsole.N64) {
                // Stick gauche analogique, stick droit pour les boutons C, croix par le hat
                view.sendMotionEvent(GLRetroView.MOTION_SOURCE_ANALOG_LEFT, x, y, 0)
                val rx = pickAxis(event, MotionEvent.AXIS_Z, MotionEvent.AXIS_RX)
                val ry = pickAxis(event, MotionEvent.AXIS_RZ, MotionEvent.AXIS_RY)
                view.sendMotionEvent(GLRetroView.MOTION_SOURCE_ANALOG_RIGHT, rx, ry, 0)
                setDirection(0, hatX < -0.5f)
                setDirection(1, hatX > 0.5f)
                setDirection(2, hatY < -0.5f)
                setDirection(3, hatY > 0.5f)
            } else {
                setDirection(0, x < -0.5f || hatX < -0.5f)
                setDirection(1, x > 0.5f || hatX > 0.5f)
                setDirection(2, y < -0.5f || hatY < -0.5f)
                setDirection(3, y > 0.5f || hatY > 0.5f)
            }
            return true
        }
        return super.dispatchGenericMotionEvent(event)
    }

    // ---------- Touches tactiles : modes d'affichage ----------

    private fun updateOverlay() {
        val overlay = touchOverlay ?: return
        val hide = when (TouchStyle.mode(this)) {
            TouchStyle.Mode.ALWAYS -> false
            TouchStyle.Mode.MANUAL -> manualHidden
            TouchStyle.Mode.AUTO -> hasGamepad()
        }
        if (hide == overlayHidden) return
        overlayHidden = hide
        val animate = TouchStyle.animations(this)
        val alpha = TouchStyle.opacity(this)
        if (hide) {
            (overlay as? PadOverlay)?.releaseAll()
            if (animate) {
                overlay.animate()
                    .alpha(0f).scaleX(0.92f).scaleY(0.92f)
                    .setDuration(250)
                    .withEndAction { if (overlayHidden) overlay.visibility = View.GONE }
                    .start()
            } else {
                overlay.alpha = 0f
                overlay.visibility = View.GONE
            }
        } else {
            overlay.visibility = View.VISIBLE
            if (animate) {
                overlay.animate()
                    .alpha(alpha).scaleX(1f).scaleY(1f)
                    .setDuration(250)
                    .start()
            } else {
                overlay.alpha = alpha
                overlay.scaleX = 1f
                overlay.scaleY = 1f
            }
        }
    }

    // ---------- Sauvegardes ----------

    private suspend fun persistSram() {
        val view = retroView ?: return
        val file = sramFile ?: return
        try {
            val bytes = view.serializeSRAM()
            if (bytes.isNotEmpty() && !bytes.contentEquals(lastSram)) {
                val tmp = File(file.path + ".tmp")
                tmp.writeBytes(bytes)
                tmp.renameTo(file)
                lastSram = bytes
            }
        } catch (e: Exception) {
            // On reessaiera au prochain passage
        }
    }

    // ---------- Jeu ----------

    private fun startGame(gameId: String, rom: File) {
        val cfg = embedded
        requestedOrientation = if (cfg != null) {
            EmbeddedGame.orientationFlag(cfg)
        } else {
            GraphicsSettings.orientationFlag(this)
        }
        val shaderConfig = if (cfg != null) {
            EmbeddedGame.shader(cfg)
        } else {
            GraphicsSettings.shaderConfig(this)
        }
        if (cfg == null) {
            Toast.makeText(
                this, "Filtre : ${GraphicsSettings.filter(this).label}", Toast.LENGTH_SHORT
            ).show()
        }

        val saves = File(filesDir, "saves").apply { mkdirs() }
        val sram = File(saves, "$gameId.sram")
        sramFile = sram
        val saved = if (sram.exists()) sram.readBytes() else null
        lastSram = saved

        console = if (cfg != null) {
            GameConsole.fromId(cfg.console)
        } else {
            GameConsole.fromLabel(GameLibrary(this).get(gameId)?.console)
        }
        mapping = ControllerMapping(this, console.buttonSet)

        val data = GLRetroViewData(this).apply {
            coreFilePath = console.coreLib
            gameFilePath = rom.absolutePath
            systemDirectory = filesDir.absolutePath
            savesDirectory = saves.absolutePath
            saveRAMState = saved
            shader = shaderConfig
        }
        val view = GLRetroView(this, data)
        retroView = view
        lifecycle.addObserver(view)

        val keyCallback: (Int, Boolean) -> Unit = { keyCode, pressed ->
            val action = if (pressed) KeyEvent.ACTION_DOWN else KeyEvent.ACTION_UP
            view.sendKeyEvent(action, keyCode, 0)
        }
        val overlay: View = if (console == GameConsole.N64) {
            N64TouchOverlay(this, TouchStyle.skinFor(this, console), keyCallback) { right, x, y ->
                val source = if (right) {
                    GLRetroView.MOTION_SOURCE_ANALOG_RIGHT
                } else {
                    GLRetroView.MOTION_SOURCE_ANALOG_LEFT
                }
                view.sendMotionEvent(source, x, y, 0)
            }
        } else {
            TouchOverlay(this, console, keyCallback)
        }
        overlay.alpha = TouchStyle.opacity(this)
        touchOverlay = overlay
        overlayHidden = false
        manualHidden = false

        val frame = FrameLayout(this)
        frame.addView(
            view,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        )
        frame.addView(
            overlay,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        )

        // Mode manuel : petit bouton discret pour afficher ou masquer les touches
        if (TouchStyle.mode(this) == TouchStyle.Mode.MANUAL) {
            val toggle = TextView(this).apply {
                text = "\u22EE"
                textSize = 20f
                setTextColor(0xFFFFFFFF.toInt())
                gravity = Gravity.CENTER
                alpha = 0.6f
                background = GradientDrawable().apply {
                    setColor(0x66000000)
                    cornerRadius = dp(18).toFloat()
                }
                setOnClickListener {
                    manualHidden = !manualHidden
                    updateOverlay()
                }
            }
            frame.addView(
                toggle,
                FrameLayout.LayoutParams(
                    dp(36), dp(36), Gravity.TOP or Gravity.CENTER_HORIZONTAL
                ).apply { topMargin = dp(8) }
            )
        }
        setContentView(frame)

        val manager = getSystemService(Context.INPUT_SERVICE) as InputManager
        inputManager = manager
        manager.registerInputDeviceListener(deviceListener, null)
        updateOverlay()

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.RESUMED) {
                while (true) {
                    delay(3000)
                    persistSram()
                }
            }
        }

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                lifecycleScope.launch {
                    withTimeoutOrNull(1500) { persistSram() }
                    finish()
                }
            }
        })
    }
}
