package com.retroengine.app

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

data class BuildRecord(
    val id: Long,
    val gameId: String,
    val gameName: String,
    val console: String,
    val pkg: String,
    val versionName: String,
    val ok: Boolean,
    val sizeKb: Long,
    val apkName: String,
    val message: String
)

object BuildHistory {

    private fun file(context: Context) = File(context.filesDir, "build_history.json")

    fun apksDir(context: Context): File = File(context.filesDir, "apks").apply { mkdirs() }

    fun all(context: Context): List<BuildRecord> {
        val f = file(context)
        if (!f.exists()) return emptyList()
        return try {
            val arr = JSONArray(f.readText())
            (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                BuildRecord(
                    o.getLong("id"),
                    o.optString("gameId"),
                    o.optString("gameName"),
                    o.optString("console"),
                    o.optString("pkg"),
                    o.optString("versionName"),
                    o.optBoolean("ok"),
                    o.optLong("sizeKb"),
                    o.optString("apkName"),
                    o.optString("message")
                )
            }.sortedByDescending { it.id }
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun write(context: Context, list: List<BuildRecord>) {
        val arr = JSONArray()
        for (r in list) {
            arr.put(
                JSONObject()
                    .put("id", r.id)
                    .put("gameId", r.gameId)
                    .put("gameName", r.gameName)
                    .put("console", r.console)
                    .put("pkg", r.pkg)
                    .put("versionName", r.versionName)
                    .put("ok", r.ok)
                    .put("sizeKb", r.sizeKb)
                    .put("apkName", r.apkName)
                    .put("message", r.message)
            )
        }
        file(context).writeText(arr.toString())
    }

    fun add(context: Context, record: BuildRecord) {
        write(context, (listOf(record) + all(context)).take(100))
    }

    fun remove(context: Context, id: Long) {
        write(context, all(context).filter { it.id != id })
    }

    fun apkFile(context: Context, record: BuildRecord): File? {
        if (record.apkName.isEmpty()) return null
        val f = File(apksDir(context), record.apkName)
        return if (f.exists()) f else null
    }

    fun deleteApk(context: Context, record: BuildRecord) {
        val name = record.apkName
        if (name.isEmpty()) return
        File(apksDir(context), name).delete()
        write(context, all(context).map { if (it.apkName == name) it.copy(apkName = "") else it })
    }

    fun deleteAllApks(context: Context) {
        apksDir(context).deleteRecursively()
        write(context, all(context).map { it.copy(apkName = "") })
    }

    fun clear(context: Context) {
        apksDir(context).deleteRecursively()
        write(context, emptyList())
    }

    fun keep(context: Context, source: File, name: String): File {
        val dest = File(apksDir(context), name)
        source.copyTo(dest, overwrite = true)
        return dest
    }
}
