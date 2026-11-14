package com.retroengine.app

import android.content.Context
import org.json.JSONObject

// Reglages de position et de taille des touches, par famille de console et par orientation
class ControlAdjust(
    context: Context,
    private val set: ButtonSet,
    private val landscape: Boolean
) {
    class G(var dx: Float = 0f, var dy: Float = 0f, var scale: Float = 1f)

    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private val groups = HashMap<String, G>()

    private fun prefix(): String = "${set.name}_${if (landscape) "L" else "P"}_"

    // Un groupe masque est simplement deplace hors de l'ecran
    fun isHidden(name: String): Boolean = get(name).dx > 10f

    fun hide(name: String) {
        val g = get(name)
        if (g.dx <= 10f) g.dx += 20f
    }

    fun show(name: String) {
        val g = get(name)
        if (g.dx > 10f) g.dx -= 20f
    }
    fun get(name: String): G = groups.getOrPut(name) { load(name) }

    private fun load(name: String): G {
        val s = prefs.getString(prefix() + name, null) ?: return G()
        return try {
            val p = s.split(",")
            G(p[0].toFloat(), p[1].toFloat(), p[2].toFloat())
        } catch (e: Exception) {
            G()
        }
    }

    fun save() {
        val ed = prefs.edit()
        for ((name, g) in groups) ed.putString(prefix() + name, "${g.dx},${g.dy},${g.scale}")
        ed.apply()
    }

    fun reset() {
        val ed = prefs.edit()
        val p = prefix()
        for (k in prefs.all.keys) if (k.startsWith(p)) ed.remove(k)
        ed.apply()
        groups.clear()
    }

    companion object {
        private const val PREFS = "controls_layout"

        // Exporte les reglages pour les integrer dans un APK de jeu
        fun exportJson(context: Context): String {
            val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            val o = JSONObject()
            for ((k, v) in prefs.all) if (v is String) o.put(k, v)
            return o.toString()
        }

        // Dans un APK de jeu : reprend une seule fois les reglages embarques
        fun importEmbedded(context: Context) {
            try {
                val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                if (prefs.getBoolean("__imported", false)) return
                val text = context.assets.open("game/controls.json").bufferedReader().use { it.readText() }
                val o = JSONObject(text)
                val ed = prefs.edit()
                for (k in o.keys()) ed.putString(k, o.getString(k))
                ed.putBoolean("__imported", true)
                ed.apply()
            } catch (e: Exception) {
                // Pas de reglages embarques
            }
        }
    }
}
