package com.retroengine.app

import android.app.AlertDialog
import android.content.Intent
import androidx.activity.ComponentActivity
import androidx.core.content.FileProvider
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

object GameApkFlow {

    fun generate(activity: ComponentActivity, gameId: String) {
        val game = GameLibrary(activity).get(gameId) ?: return
        val steps = StringBuffer()
        val dialog = AlertDialog.Builder(activity)
            .setTitle("Génération de l'APK")
            .setMessage("Préparation…")
            .setCancelable(false)
            .show()

        activity.lifecycleScope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    GameApkMaker.build(activity, game) { step ->
                        steps.append("• ").append(step).append('\n')
                        activity.runOnUiThread { dialog.setMessage(steps.toString()) }
                    }
                }
            }
            dialog.dismiss()
            val file = result.getOrNull()
            if (file != null) {
                AlertDialog.Builder(activity)
                    .setTitle("APK créé")
                    .setMessage(
                        "${game.name}\n" +
                            "Identifiant : ${GameApkMaker.packageFor(game)}\n" +
                            "Taille : ${file.length() / 1024} Ko\n\n" +
                            "$steps\nSignature vérifiée."
                    )
                    .setPositiveButton("Installer") { _, _ -> GameApkBuilder.install(activity, file) }
                    .setNeutralButton("Partager") { _, _ -> share(activity, file) }
                    .setNegativeButton("Fermer", null)
                    .show()
            } else {
                val e = result.exceptionOrNull()
                AlertDialog.Builder(activity)
                    .setTitle("Échec de la génération")
                    .setMessage(
                        "${e?.javaClass?.simpleName} : ${e?.message}\n\nÉtapes réalisées :\n$steps"
                    )
                    .setPositiveButton("OK", null)
                    .show()
            }
        }
    }

    private fun share(activity: ComponentActivity, file: File) {
        val uri = FileProvider.getUriForFile(activity, "${activity.packageName}.fileprovider", file)
        val send = Intent(Intent.ACTION_SEND)
            .setType("application/vnd.android.package-archive")
            .putExtra(Intent.EXTRA_STREAM, uri)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        activity.startActivity(Intent.createChooser(send, "Enregistrer ou partager l'APK"))
    }
}
