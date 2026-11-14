package com.retroengine.app

import android.content.Context

// Reglages d'affichage des touches tactiles : mode, animations, opacite, couleur
object TouchStyle {

    private const val PREFS = "touch_style"

    enum class Mode(val label: String) {
        AUTO("Automatique : masquées dès qu'une manette est connectée"),
        ALWAYS("Toujours affichées"),
        MANUAL("Manuel : un petit bouton les affiche ou les masque")
    }

    class ColorChoice(val key: String, val label: String, val colors: ButtonColors?)

    val choices: List<ColorChoice> = listOf(
        ColorChoice("DEFAULT", "Couleurs de la console", null),
        ColorChoice("RED", "Rouge",
            ButtonColors(0xFFFF7A7A.toInt(), 0xFFD32F2F.toInt(), 0xFF7F1414.toInt())),
        ColorChoice("BLUE", "Bleu",
            ButtonColors(0xFF7AA8FF.toInt(), 0xFF1E5BD6.toInt(), 0xFF0F2F7A.toInt())),
        ColorChoice("GREEN", "Vert",
            ButtonColors(0xFF8CE08C.toInt(), 0xFF2E9E3E.toInt(), 0xFF145A20.toInt())),
        ColorChoice("YELLOW", "Jaune",
            ButtonColors(0xFFFFE680.toInt(), 0xFFE0B010.toInt(), 0xFF8A6A00.toInt())),
        ColorChoice("PURPLE", "Violet",
            ButtonColors(0xFFC8A8FF.toInt(), 0xFF7A4FE0.toInt(), 0xFF35177F.toInt())),
        ColorChoice("GRAY", "Gris",
            ButtonColors(0xFFB0B0BA.toInt(), 0xFF5A5A66.toInt(), 0xFF22222A.toInt()))
    )

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun mode(context: Context): Mode {
        val name = prefs(context).getString("mode", Mode.AUTO.name)
        return Mode.values().firstOrNull { it.name == name } ?: Mode.AUTO
    }

    fun setMode(context: Context, mode: Mode) {
        prefs(context).edit().putString("mode", mode.name).apply()
    }

    fun animations(context: Context): Boolean = prefs(context).getBoolean("animations", true)

    fun setAnimations(context: Context, on: Boolean) {
        prefs(context).edit().putBoolean("animations", on).apply()
    }

    fun opacityPercent(context: Context): Int =
        prefs(context).getInt("opacity", 100).coerceIn(30, 100)

    fun opacity(context: Context): Float = opacityPercent(context) / 100f

    fun setOpacity(context: Context, percent: Int) {
        prefs(context).edit().putInt("opacity", percent.coerceIn(30, 100)).apply()
    }

    fun colorKey(context: Context): String =
        prefs(context).getString("color", "DEFAULT") ?: "DEFAULT"

    fun setColor(context: Context, key: String) {
        prefs(context).edit().putString("color", key).apply()
    }

    // Habillage des boutons : celui de la console, ou une couleur choisie pour tous
    fun skinFor(context: Context, console: GameConsole): ConsoleSkin {
        val choice = choices.firstOrNull { it.key == colorKey(context) }
        val c = choice?.colors ?: return console.skin
        return ConsoleSkin(c, c, c, c, c, c)
    }

    fun reset(context: Context) {
        prefs(context).edit().clear().apply()
    }
}
