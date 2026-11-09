package com.retroengine.app

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.widget.Toast
import androidx.core.content.FileProvider
import java.io.File
import java.io.IOException
import java.util.zip.ZipFile

object GameApkBuilder {

    private fun isClass(name: String, loader: ClassLoader): Boolean =
        try {
            Class.forName(name, false, loader)
            true
        } catch (e: Throwable) {
            false
        }

    // Fabrique une copie de l'APK de Retro Engine sous un autre nom et un autre identifiant.
    fun buildCopy(
        context: Context,
        newPackage: String,
        newLabel: String,
        log: (String) -> Unit
    ): File {
        val oldPackage = context.packageName
        val template = File(context.applicationInfo.sourceDir)
        val outDir = File(context.cacheDir, "generated").apply { mkdirs() }
        val unsigned = File(outDir, "unsigned.apk")
        val signed = File(outDir, "$newPackage.apk")
        unsigned.delete()
        signed.delete()

        log("Lecture du modèle d'APK")
        val manifest = ZipFile(template).use { zip ->
            val entry = zip.getEntry("AndroidManifest.xml")
                ?: throw IOException("AndroidManifest.xml introuvable dans le modèle.")
            zip.getInputStream(entry).use { it.readBytes() }
        }

        log("Modification du manifeste")
        val loader = context.classLoader
        val patched = AxmlPatcher.patchStrings(manifest) { s ->
            when {
                s == oldPackage -> newPackage
                s == "Retro Engine" -> newLabel
                s.startsWith("$oldPackage.") && !isClass(s, loader) ->
                    newPackage + s.substring(oldPackage.length)
                else -> s
            }
        }

        log("Assemblage de l'APK")
        ApkRepacker.repack(template, unsigned, mapOf("AndroidManifest.xml" to patched), emptyMap())

        log("Signature de l'APK")
        ApkSigning.sign(unsigned, signed)
        unsigned.delete()

        log("Vérification de la signature")
        ApkSigning.verify(signed)
        return signed
    }

    fun install(activity: Activity, file: File) {
        if (!activity.packageManager.canRequestPackageInstalls()) {
            Toast.makeText(
                activity,
                "Autorise Retro Engine à installer des applis, puis recommence.",
                Toast.LENGTH_LONG
            ).show()
            activity.startActivity(
                Intent(
                    Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                    Uri.parse("package:${activity.packageName}")
                )
            )
            return
        }
        val uri = FileProvider.getUriForFile(activity, "${activity.packageName}.fileprovider", file)
        activity.startActivity(
            Intent(Intent.ACTION_VIEW)
                .setDataAndType(uri, "application/vnd.android.package-archive")
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        )
    }
}
