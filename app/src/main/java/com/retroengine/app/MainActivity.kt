package com.retroengine.app

import android.content.Context
import android.content.Intent
import android.hardware.input.InputManager
import android.net.Uri
import android.os.Bundle
import android.view.Gravity
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.swordfish.libretrodroid.GLRetroView
import com.swordfish.libretrodroid.GLRetroViewData
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File
import java.security.MessageDigest

class MainActivity : ComponentActivity() {

    private var retroView: GLRetroView? = null
    private var touchOverlay: TouchOverlay? = null
    private var overlayHidden = false
    private var sramFile: File? = null
    private var lastSram: ByteArray? = null
    private var inputManager: InputManager? = null
    private var mapping: ControllerMapping? = null

    // Etat des 4 directions envoyees par stick ou croix de la manette
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

    private val pickRom =
        registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            if (uri != null) startGame(uri)
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val logo = ImageView(this).apply {
            setImageResource(R.drawable.retro_logo)
            scaleType = ImageView.ScaleType.FIT_CENTER
        }
        val romButton = Button(this).apply {
            text = "Choisir une ROM NES"
            setOnClickListener { pickRom.launch(arrayOf("*/*")) }
        }
        val configButton = Button(this).apply {
            text = "Configurer la manette"
            setOnClickListener {
                startActivity(Intent(this@MainActivity, ControllerConfigActivity::class.java))
            }
        }
        val matchWidth = {
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(64, 160, 64, 200)
            addView(
                logo,
                LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f)
            )
            addView(romButton, matchWidth())
            addView(configButton, matchWidth())
        }
        setContentView(root)
    }

    override fun onDestroy() {
        inputManager?.unregisterInputDeviceListener(deviceListener)
        super.onDestroy()
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

    private fun startGame(uri: Uri) {
        val rom = File(cacheDir, "rom.nes")
        val digest = MessageDigest.getInstance("SHA-1")
        try {
            contentResolver.openInputStream(uri)?.use { input ->
                rom.outputStream().use { output ->
                    val buffer = ByteArray(64 * 1024)
                    while (true) {
                        val n = input.read(buffer)
                        if (n == -1) break
                        output.write(buffer, 0, n)
                        digest.update(buffer, 0, n)
                    }
                }
            }
        } catch (e: Exception) {
            Toast.makeText(this, "Impossible de lire la ROM : ${e.message}", Toast.LENGTH_LONG).show()
            return
        }
        val romId = digest.digest().joinToString("") { "%02x".format(it) }

        mapping = ControllerMapping(this)

        val saves = File(filesDir, "saves").apply { mkdirs() }
        val sram = File(saves, "$romId.sram")
        sramFile = sram
        val saved = if (sram.exists()) sram.readBytes() else null
        lastSram = saved

        val data = GLRetroViewData(this).apply {
            coreFilePath = "libfceumm_libretro_android.so"
            gameFilePath = rom.absolutePath
            systemDirectory = filesDir.absolutePath
            savesDirectory = saves.absolutePath
            saveRAMState = saved
        }
        val view = GLRetroView(this, data)
        retroView = view
        lifecycle.addObserver(view)

        val overlay = TouchOverlay(this) { keyCode, pressed ->
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

        // Masquage automatique des touches tactiles selon les manettes connectees
        val manager = getSystemService(Context.INPUT_SERVICE) as InputManager
        inputManager = manager
        manager.registerInputDeviceListener(deviceListener, null)
        updateOverlay()

        // Sauvegarde automatique toutes les 3 secondes pendant le jeu
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.RESUMED) {
                while (true) {
                    delay(3000)
                    persistSram()
                }
            }
        }

        // Sauvegarde finale quand on appuie sur Retour
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
