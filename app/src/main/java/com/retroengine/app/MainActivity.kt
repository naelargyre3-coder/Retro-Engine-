package com.retroengine.app

import android.net.Uri
import android.os.Bundle
import android.view.Gravity
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import com.swordfish.libretrodroid.GLRetroView
import com.swordfish.libretrodroid.GLRetroViewData
import java.io.File

class MainActivity : ComponentActivity() {

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

    private fun startGame(uri: Uri) {
        val rom = File(cacheDir, "rom.nes")
        try {
            contentResolver.openInputStream(uri)?.use { input ->
                rom.outputStream().use { output -> input.copyTo(output) }
            }
        } catch (e: Exception) {
            Toast.makeText(this, "Impossible de lire la ROM : ${e.message}", Toast.LENGTH_LONG).show()
            return
        }

        val saves = File(filesDir, "saves").apply { mkdirs() }
        val data = GLRetroViewData(this).apply {
            coreFilePath = "libfceumm_libretro_android.so"
            gameFilePath = rom.absolutePath
            systemDirectory = filesDir.absolutePath
            savesDirectory = saves.absolutePath
        }
        val retroView = GLRetroView(this, data)
        lifecycle.addObserver(retroView)
        setContentView(retroView)
    }
}
