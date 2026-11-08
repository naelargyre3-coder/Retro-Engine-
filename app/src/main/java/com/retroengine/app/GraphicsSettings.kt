package com.retroengine.app

import android.content.Context
import android.content.pm.ActivityInfo
import com.swordfish.libretrodroid.ShaderConfig

enum class GameFilter(
    val label: String,
    val description: String,
    val available: Boolean,
    val note: String = ""
) {
    PIXEL("Pixel Art", "Pixels nets, sans flou. Garde l'aspect d'origine.", true),
    CRT("CRT / Scanlines", "Simule les lignes d'un \u00e9cran cathodique.", true),
    SMOOTH("Lissage", "Image adoucie, contours moins carr\u00e9s.", true),
    XBR2("2xBR", "Lissage des contours des sprites.", false, "Non pris en charge par le moteur actuel."),
    XBR4("4xBR", "Lissage plus pouss\u00e9.", false, "Non pris en charge par le moteur actuel.")
}

enum class OrientationMode(val label: String, val flag: Int) {
    AUTO("Rotation automatique", ActivityInfo.SCREEN_ORIENTATION_FULL_SENSOR),
    PORTRAIT("Portrait", ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT),
    LANDSCAPE("Paysage", ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE)
}

object GraphicsSettings {

    private fun prefs(context: Context) =
        context.getSharedPreferences("graphics", Context.MODE_PRIVATE)

    fun filter(context: Context): GameFilter {
        val name = prefs(context).getString("filter", GameFilter.PIXEL.name)
        return GameFilter.values().firstOrNull { it.name == name && it.available }
            ?: GameFilter.PIXEL
    }

    fun setFilter(context: Context, filter: GameFilter) {
        prefs(context).edit().putString("filter", filter.name).apply()
    }

    fun orientation(context: Context): OrientationMode {
        val name = prefs(context).getString("orientation", OrientationMode.AUTO.name)
        return OrientationMode.values().firstOrNull { it.name == name } ?: OrientationMode.AUTO
    }

    fun setOrientation(context: Context, mode: OrientationMode) {
        prefs(context).edit().putString("orientation", mode.name).apply()
    }

    fun orientationFlag(context: Context): Int = orientation(context).flag

    fun shaderConfig(context: Context): ShaderConfig = when (filter(context)) {
        GameFilter.CRT -> ShaderConfig.CRT
        GameFilter.SMOOTH -> ShaderConfig.Default
        else -> ShaderConfig.Sharp
    }

    fun reset(context: Context) {
        prefs(context).edit().clear().apply()
    }
}
