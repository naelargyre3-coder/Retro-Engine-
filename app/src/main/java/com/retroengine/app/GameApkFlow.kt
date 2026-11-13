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

    fun generate(activity: ComponentActivity, gameId: String, onDone: () -> Unit = {}) {
        val game = GameLibrary(activity).get(gameId) ?: return
        val pkg = GameApkMaker.packageOf(game)
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
            val built = result.getOrNull()
            val now = System.currentTimeMillis()
            if (built != null) {
                val safeVersion = game.versionName.replace(Regex("[^0-9A-Za-z._-]"), "_")
                val name = "$pkg-$safeVersion.apk"
                val kept = BuildHistory.keep(activity, built, name)
                built.delete()
                BuildHistory.add(
                    activity,
                    BuildRecord(
                        now, game.id, game.name, game.console, pkg, game.versionName,
                        true, kept.length() / 1024, name, "Signature vérifiée"
                    )
                )
                onDone()
                AlertDialog.Builder(activity)
                    .setTitle("APK créé")
                    .setMessage(
                        "${game.name} (version ${game.versionName})\n" +
                            "Identifiant : $pkg\n" +
                            "Taille : ${kept.length() / 1024} Ko\n\n" +
                            "$steps\nSignature vérifiée.\n" +
                            "L'APK est conservé dans l'historique."
                    )
                    .setPositiveButton("Installer") { _, _ -> GameApkBuilder.install(activity, kept) }
                    .setNeutralButton("Partager") { _, _ -> share(activity, kept) }
                    .setNegativeButton("Fermer", null)
                    .show()
            } else {
                val e = result.exceptionOrNull()
                val message = "${e?.javaClass?.simpleName} : ${e?.message}"
                BuildHistory.add(
                    activity,
                    BuildRecord(
                        now, game.id, game.name, game.console, pkg, game.versionName,
                        false, 0, "", "$message\n\nÉtapes réalisées :\n$steps"
                    )
                )
                onDone()
                AlertDialog.Builder(activity)
                    .setTitle("Échec de la génération")
                    .setMessage("$message\n\nÉtapes réalisées :\n$steps")
                    .setPositiveButton("OK", null)
                    .show()
            }
        }
    }

    fun share(activity: ComponentActivity, file: File) {
        val uri = FileProvider.getUriForFile(activity, "${activity.packageName}.fileprovider", file)
        val send = Intent(Intent.ACTION_SEND)
            .setType("application/vnd.android.package-archive")
            .putExtra(Intent.EXTRA_STREAM, uri)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        activity.startActivity(Intent.createChooser(send, "Enregistrer ou partager l'APK"))
    }
}
