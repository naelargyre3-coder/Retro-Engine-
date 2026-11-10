package com.retroengine.app

import android.content.Context
import android.hardware.input.InputManager
import android.os.Bundle
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
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

class GameActivity : ComponentActivity() {

    private var retroView: GLRetroView? = null
    private var touchOverlay: TouchOverlay? = null
    private var overlayHidden = false
    private var sramFile: File? = null
    private var lastSram: ByteArray? = null
    private var inputManager: InputManager? = null
    private var mapping: ControllerMapping? = null
    private var embedded: EmbeddedConfig? = null

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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val cfg = if (intent.getBooleanExtra("embedded", false)) EmbeddedGame.load(this) else null
        embedded = cfg

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

    override fun dispatchGenericMotionEvent(event: MotionEvent): Boolean {
        if (retroView != null && isGamepadSource(event.source) &&
            event.actionMasked == MotionEvent.ACTION_MOVE
        ) {
            val x = event.getAxisValue(MotionEvent.AXIS_X)
            val y = event.getAxisValue(MotionEvent.AXIS_Y)
            val hatX = event.getAxisValue(MotionEvent.AXIS_HAT_X)
            val hatY = event.getAxisValue(MotionEvent.AXIS_HAT_Y)
            setDirection(0, x < -0.5f || hatX < -0.5f)
            setDirection(1, x > 0.5f || hatX > 0.5f)
            setDirection(2, y < -0.5f || hatY < -0.5f)
            setDirection(3, y > 0.5f || hatY > 0.5f)
            return true
        }
        return super.dispatchGenericMotionEvent(event)
    }

    private fun updateOverlay() {
        val overlay = touchOverlay ?: return
        val hide = hasGamepad()
        if (hide == overlayHidden) return
        overlayHidden = hide
        if (hide) {
            overlay.releaseAll()
            overlay.animate()
                .alpha(0f).scaleX(0.92f).scaleY(0.92f)
                .setDuration(250)
                .withEndAction { if (overlayHidden) overlay.visibility = View.GONE }
                .start()
        } else {
            overlay.visibility = View.VISIBLE
            overlay.animate()
                .alpha(1f).scaleX(1f).scaleY(1f)
                .setDuration(250)
                .start()
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

        mapping = ControllerMapping(this)

        val saves = File(filesDir, "saves").apply { mkdirs() }
        val sram = File(saves, "$gameId.sram")
        sramFile = sram
        val saved = if (sram.exists()) sram.readBytes() else null
        lastSram = saved

        val console = if (cfg != null) {
            GameConsole.fromId(cfg.console)
        } else {
            GameConsole.fromLabel(GameLibrary(this).get(gameId)?.console)
        } 
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

            val overlay = TouchOverlay(this, console.skin) { keyCode, pressed ->
            val action = if (pressed) KeyEvent.ACTION_DOWN else KeyEvent.ACTION_UP
            view.sendKeyEvent(action, keyCode, 0)
        }
        touchOverlay = overlay
        overlayHidden = false

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
