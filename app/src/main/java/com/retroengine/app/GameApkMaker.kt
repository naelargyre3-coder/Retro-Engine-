package com.retroengine.app

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.text.Normalizer
import java.util.zip.ZipFile

object GameApkMaker {

    // Identifiant Android unique et valide, stable pour un même jeu
    fun packageFor(game: GameEntry): String {
        val plain = Normalizer.normalize(game.name, Normalizer.Form.NFD)
            .replace(Regex("\\p{M}+"), "")
        var slug = plain.lowercase().replace(Regex("[^a-z0-9]+"), "")
        if (slug.isEmpty()) slug = "jeu"
        if (slug.length > 20) slug = slug.substring(0, 20)
        if (slug[0].isDigit()) slug = "g$slug"
        return "com.retroengine.game.${slug}_${game.id.substring(0, 6)}"
    }

    private fun isClass(name: String, loader: ClassLoader): Boolean =
        try {
            Class.forName(name, false, loader)
            true
        } catch (e: Throwable) {
            false
        }

    // Icône adaptative : le logo est placé dans la zone sûre, centré
    private fun foregroundPng(iconFile: File): ByteArray {
        val src = BitmapFactory.decodeFile(iconFile.path)
            ?: throw IOException("Logo illisible.")
        val size = 432
        val out = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)
        val side = size * 0.70f
        val offset = (size - side) / 2f
        canvas.drawBitmap(
            src, null,
            RectF(offset, offset, offset + side, offset + side),
            Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)
        )
        val bytes = ByteArrayOutputStream()
        out.compress(Bitmap.CompressFormat.PNG, 100, bytes)
        return bytes.toByteArray()
    }

    fun build(context: Context, game: GameEntry, log: (String) -> Unit): File {
        val library = GameLibrary(context)
        val romFile = library.romFile(game.id)
        if (!romFile.exists()) throw IOException("ROM introuvable pour ce jeu.")

        val newPackage = packageFor(game)
        val oldPackage = context.packageName
        val template = File(context.applicationInfo.sourceDir)
        val outDir = File(context.cacheDir, "generated").apply { mkdirs() }
        val unsigned = File(outDir, "unsigned.apk")
        val signed = File(outDir, "$newPackage.apk")
        unsigned.delete()
        signed.delete()

        log("Lecture du modèle d'APK")
        var manifest: ByteArray
        var iconEntry: String?
        ZipFile(template).use { zip ->
            val entry = zip.getEntry("AndroidManifest.xml")
                ?: throw IOException("AndroidManifest.xml introuvable dans le modèle.")
            manifest = zip.getInputStream(entry).use { it.readBytes() }
            iconEntry = zip.entries().asSequence()
                .map { it.name }
                .firstOrNull { it.endsWith("ic_launcher_foreground.png") }
        }

        log("Personnalisation du manifeste (nom, identifiant)")
        val loader = context.classLoader
        val patched = AxmlPatcher.patchStrings(manifest) { s ->
            when {
                s == oldPackage -> newPackage
                s == "Retro Engine" -> game.name
                s.startsWith("$oldPackage.") && !isClass(s, loader) ->
                    newPackage + s.substring(oldPackage.length)
                else -> s
            }
        }
        val replace = HashMap<String, ByteArray>()
        replace["AndroidManifest.xml"] = patched

        val iconFile = library.iconFile(game.id)
        val entryName = iconEntry
        if (iconFile.exists() && entryName != null) {
            log("Intégration du logo")
            replace[entryName] = foregroundPng(iconFile)
        } else if (iconFile.exists()) {
            log("Logo : emplacement d'icône introuvable, icône par défaut utilisée")
        } else {
            log("Pas de logo choisi : icône par défaut")
        }

        log("Intégration de la ROM et des réglages")
        val romBytes = romFile.readBytes()
        val config = JSONObject()
            .put("id", game.id)
            .put("name", game.name)
            .put("console", GameConsole.fromLabel(game.console).name)
            .put("filter", GraphicsSettings.filter(context).name)
            .put("orientation", GraphicsSettings.orientation(context).name)
        val add = LinkedHashMap<String, ByteArray>()
        add["assets/game/rom.bin"] = romBytes
        add["assets/game/game.json"] = config.toString().toByteArray(Charsets.UTF_8)
        add["assets/game/controls.json"] = ControlAdjust.exportJson(context).toByteArray(Charsets.UTF_8)
       
        log("Assemblage de l'APK")
        ApkRepacker.repack(template, unsigned, replace, add)

       log("Signature de l'APK")
        ApkSigning.sign(unsigned, signed)
        unsigned.delete()

        log("Vérification de la signature")
        ApkSigning.verify(signed)

        log("Contrôle du contenu")
        ZipFile(signed).use { zip ->
            val rom = zip.getEntry("assets/game/rom.bin")
                ?: throw IOException("La ROM est absente de l'APK généré.")
            if (rom.size != romBytes.size.toLong()) {
                throw IOException("La ROM est incomplète dans l'APK généré.")
            }
            if (zip.getEntry("assets/game/game.json") == null) {
                throw IOException("Les réglages sont absents de l'APK généré.")
            }
        }
        return signed
    }
}
