package com.retroengine.app

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.security.MessageDigest

data class GameEntry(
    val id: String,
    val name: String,
    val console: String,
    val addedAt: Long,
    val pkg: String = "",
    val versionName: String = "1.0",
    val versionCode: Int = 1,
    val developer: String = "",
    val description: String = ""
)

class GameLibrary(private val context: Context) {

    private val root = File(context.filesDir, "games").apply { mkdirs() }
    private val indexFile = File(root, "games.json")

    fun romFile(id: String): File {
        val dir = File(root, id)
        for (ext in GameConsole.values().map { it.romExt }.distinct()) {
            val f = File(dir, "rom.$ext")
            if (f.exists()) return f
        }
        return File(dir, "rom.nes")
    }

    fun iconFile(id: String): File = File(File(root, id), "icon.png")

    fun hasSave(id: String): Boolean =
        File(File(context.filesDir, "saves"), "$id.sram").exists()

    fun all(): List<GameEntry> {
        if (!indexFile.exists()) return emptyList()
        return try {
            val arr = JSONArray(indexFile.readText())
            (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                GameEntry(
                    o.getString("id"),
                    o.getString("name"),
                    o.optString("console", "NES"),
                    o.optLong("addedAt", 0L),
                    o.optString("pkg", ""),
                    o.optString("versionName", "1.0"),
                    o.optInt("versionCode", 1),
                    o.optString("developer", ""),
                    o.optString("description", "")
                )
            }.sortedByDescending { it.addedAt }
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun get(id: String): GameEntry? = all().firstOrNull { it.id == id }

    private fun write(list: List<GameEntry>) {
        val arr = JSONArray()
        for (g in list) {
            arr.put(
                JSONObject()
                    .put("id", g.id)
                    .put("name", g.name)
                    .put("console", g.console)
                    .put("addedAt", g.addedAt)
                    .put("pkg", g.pkg)
                    .put("versionName", g.versionName)
                    .put("versionCode", g.versionCode)
                    .put("developer", g.developer)
                    .put("description", g.description)
            )
        }
        indexFile.writeText(arr.toString())
    }

    private fun rawName(uri: Uri): String {
        var name = ""
        try {
            context.contentResolver
                .query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
                ?.use { c ->
                    if (c.moveToFirst()) name = c.getString(0) ?: ""
                }
        } catch (e: Exception) {
            // Nom inconnu
        }
        return name
    }

    fun displayName(uri: Uri): String {
        val name = rawName(uri).ifBlank { "Jeu" }
        return name.substringBeforeLast('.')
    }

    // Copie la ROM dans la bibliotheque apres detection de la console.
    fun import(uri: Uri): GameEntry {
        val tmp = File(root, "import.tmp")
        val digest = MessageDigest.getInstance("SHA-1")
        val input = context.contentResolver.openInputStream(uri)
            ?: throw IOException("Fichier illisible.")
        input.use { stream ->
            tmp.outputStream().use { out ->
                val buffer = ByteArray(64 * 1024)
                while (true) {
                    val n = stream.read(buffer)
                    if (n == -1) break
                    out.write(buffer, 0, n)
                    digest.update(buffer, 0, n)
                }
            }
        }

        val header = ByteArray(0x10200)
        var read = 0
        tmp.inputStream().use { s ->
            while (read < header.size) {
                val r = s.read(header, read, header.size - read)
                if (r < 0) break
                read += r
            }
        }
        if (read >= 4 && header[0] == 0x50.toByte() && header[1] == 0x4B.toByte()) {
            tmp.delete()
            throw IOException("Ce fichier est un zip : décompresse-le avant de l'ajouter.")
        }
        val ext = rawName(uri).substringAfterLast('.', "").lowercase()
        val console = GameConsole.detect(header.copyOf(read), ext)
        if (console == null) {
            tmp.delete()
            throw IOException(
                "ROM non reconnue. Formats pris en charge : NES, Game Boy, Game Boy Color, " +
                    "SNES, GBA, Mega Drive et N64 (ROM non zippées)."
            )
        }

        val id = digest.digest().joinToString("") { "%02x".format(it) }
        get(id)?.let {
            tmp.delete()
            return it
        }
        File(root, id).mkdirs()
        val target = File(File(root, id), "rom.${console.romExt}")
        if (!tmp.renameTo(target)) {
            tmp.copyTo(target, overwrite = true)
            tmp.delete()
        }
        val base = GameEntry(id, displayName(uri), console.label, System.currentTimeMillis())
        val entry = base.copy(pkg = GameApkMaker.packageFor(base))
        write(all() + entry)
        return entry
    }

    fun rename(id: String, name: String) {
        write(all().map { if (it.id == id) it.copy(name = name) else it })
    }

    fun updateApkInfo(
        id: String,
        pkg: String,
        versionName: String,
        developer: String,
        description: String
    ) {
        write(all().map {
            if (it.id == id) {
                it.copy(
                    pkg = pkg,
                    versionName = versionName,
                    developer = developer,
                    description = description
                )
            } else {
                it
            }
        })
    }

    fun packageTaken(pkg: String, exceptId: String): Boolean =
        all().any { it.id != exceptId && it.pkg == pkg }

    fun delete(id: String) {
        File(root, id).deleteRecursively()
        File(File(context.filesDir, "saves"), "$id.sram").delete()
        write(all().filter { it.id != id })
    }
}
