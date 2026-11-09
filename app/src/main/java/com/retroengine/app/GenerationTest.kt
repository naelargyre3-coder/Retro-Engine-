package com.retroengine.app

import android.app.AlertDialog
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

object GenerationTest {

    fun run(activity: ComponentActivity) {
        val steps = StringBuffer()
        Toast.makeText(activity, "Génération en cours…", Toast.LENGTH_SHORT).show()
        activity.lifecycleScope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    GameApkBuilder.buildCopy(
                        activity, "com.retroengine.copy1", "Retro Engine Copie"
                    ) { steps.append("• ").append(it).append('\n') }
                }
            }
            val file = result.getOrNull()
            if (file != null) {
                AlertDialog.Builder(activity)
                    .setTitle("APK créé")
                    .setMessage("$steps\nFichier : ${file.name} (${file.length() / 1024} Ko)\nSignature vérifiée.")
                    .setPositiveButton("Installer") { _, _ -> GameApkBuilder.install(activity, file) }
                    .setNegativeButton("Fermer", null)
                    .show()
            } else {
                val e = result.exceptionOrNull()
                AlertDialog.Builder(activity)
                    .setTitle("Échec de la génération")
                    .setMessage("${e?.javaClass?.simpleName} : ${e?.message}\n\nÉtapes réalisées :\n$steps")
                    .setPositiveButton("OK", null)
                    .show()
            }
        }
    }
}
