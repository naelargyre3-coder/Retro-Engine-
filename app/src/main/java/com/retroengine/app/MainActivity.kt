package com.retroengine.app

import android.net.Uri
import android.os.Bundle
import android.view.Gravity
import android.view.KeyEvent
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
    private var sramFile: File? = null
    private var lastSram: ByteArray? = null

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
        val button = Button(this).apply {
            text = "Choisir une ROM NES"
            setOnClickListener { pickRom.launch(arrayOf("*/*")) }
        }
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(64, 160, 64, 200)
            addView(
                logo,
                LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f)
            )
            addView(
                button,
                LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
            )
        }
        setContentView(root)
    }

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
