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
    val addedAt: Long
)

class GameLibrary(private val context: Context) {

    private val root = File(context.filesDir, "games").apply { mkdirs() }
    private val indexFile = File(root, "games.json")

    fun romFile(id: String): File = File(File(root, id), "rom.nes")
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
                    o.optLong("addedAt", 0L)
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
            )
        }
        indexFile.writeText(arr.toString())
    }

    fun displayName(uri: Uri): String {
        var name = "Jeu NES"
        try {
            context.contentResolver
                .query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
                ?.use { c ->
                    if (c.moveToFirst()) {
                        val n = c.getString(0)
                        if (!n.isNullOrBlank()) name = n
                    }
                }
        } catch (e: Exception) {
            // On garde le nom par defaut
        }
        return name.substringBeforeLast('.')
    }

    // Copie la ROM dans la bibliotheque apres verification de son format.
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

        val header = ByteArray(4)
        val read = tmp.inputStream().use { it.read(header) }
        val valid = read == 4 &&
            header[0] == 'N'.code.toByte() &&
            header[1] == 'E'.code.toByte() &&
            header[2] == 'S'.code.toByte() &&
            header[3] == 0x1A.toByte()
        if (!valid) {
            tmp.delete()
            throw IOException("Ce fichier n'est pas une ROM NES valide (format .nes attendu).")
        }

        val id = digest.digest().joinToString("") { "%02x".format(it) }
        get(id)?.let {
            tmp.delete()
            return it
        }
        File(root, id).mkdirs()
        val target = romFile(id)
        if (!tmp.renameTo(target)) {
            tmp.copyTo(target, overwrite = true)
            tmp.delete()
        }
        val entry = GameEntry(id, displayName(uri), "NES", System.currentTimeMillis())
        write(all() + entry)
        return entry
    }

    fun rename(id: String, name: String) {
        write(all().map { if (it.id == id) it.copy(name = name) else it })
    }

    fun delete(id: String) {
        File(root, id).deleteRecursively()
        File(File(context.filesDir, "saves"), "$id.sram").delete()
        write(all().filter { it.id != id })
    }
}
