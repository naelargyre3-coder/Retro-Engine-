package com.retroengine.app

import android.content.Context
import android.content.pm.ActivityInfo
import com.swordfish.libretrodroid.ShaderConfig
import org.json.JSONObject
import java.io.File

data class EmbeddedConfig(
    val id: String,
    val name: String,
    val filter: String,
    val orientation: String
)

// Jeu embarqué dans l'APK généré (ROM et réglages dans les assets)
object EmbeddedGame {

    fun load(context: Context): EmbeddedConfig? {
        return try {
            val text = context.assets.open("game/game.json").bufferedReader().use { it.readText() }
            val o = JSONObject(text)
            EmbeddedConfig(
                o.getString("id"),
                o.getString("name"),
                o.optString("filter", "PIXEL"),
                o.optString("orientation", "AUTO")
            )
        } catch (e: Exception) {
            null
        }
    }

    fun romFile(context: Context, cfg: EmbeddedConfig): File {
        val dir = File(context.filesDir, "embedded").apply { mkdirs() }
        val rom = File(dir, "${cfg.id}.nes")
        if (!rom.exists()) {
            val tmp = File(dir, "${cfg.id}.tmp")
            context.assets.open("game/rom.nes").use { input ->
                tmp.outputStream().use { output -> input.copyTo(output) }
            }
            tmp.renameTo(rom)
        }
        return rom
    }

    fun shader(cfg: EmbeddedConfig): ShaderConfig = when (cfg.filter) {
        "CRT" -> ShaderConfig.CRT
        "SMOOTH" -> ShaderConfig.Default
        else -> ShaderConfig.Sharp
    }

    fun orientationFlag(cfg: EmbeddedConfig): Int = when (cfg.orientation) {
        "PORTRAIT" -> ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT
        "LANDSCAPE" -> ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        else -> ActivityInfo.SCREEN_ORIENTATION_FULL_SENSOR
    }
}
