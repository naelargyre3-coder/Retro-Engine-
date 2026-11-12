package com.retroengine.app

import android.content.res.Configuration
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.ComponentActivity

class ControlsEditorActivity : ComponentActivity() {

    private var console = GameConsole.NES
    private var overlay: View? = null
    private var selectedName: String? = null
    private lateinit var root: FrameLayout
    private lateinit var info: TextView

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        root = FrameLayout(this).apply { setBackgroundColor(0xFF010317.toInt()) }
        info = TextView(this).apply {
            textSize = 13f
            setTextColor(0xFFFFD54F.toInt())
            gravity = Gravity.CENTER
            setPadding(dp(16), dp(4), dp(16), dp(4))
        }
        root.addView(
            buildToolbar(),
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.TOP
            )
        )
        root.addView(
            info,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.CENTER
            )
        )
        setContentView(root)
        buildOverlay()
    }

    private fun toolbarButton(label: String, onClick: () -> Unit): Button = Button(this).apply {
        text = label
        textSize = 12f
        setOnClickListener { onClick() }
    }

    private fun buildToolbar(): View {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setBackgroundColor(0xCC010317.toInt())
        }
        fun pick(label: String, target: GameConsole) {
            row.addView(toolbarButton(label) {
                console = target
                selectedName = null
                buildOverlay()
            })
        }
        pick("NES / GB", GameConsole.NES)
        pick("GBA", GameConsole.GBA)
        pick("SNES", GameConsole.SNES)
        pick("MD", GameConsole.MD)
        pick("N64", GameConsole.N64)
        row.addView(toolbarButton("\u2212") { (overlay as? EditableOverlay)?.scaleSelected(0.9f) })
        row.addView(toolbarButton("+") { (overlay as? EditableOverlay)?.scaleSelected(1.1f) })
        row.addView(toolbarButton("Défaut") { (overlay as? EditableOverlay)?.resetAdjust() })
        row.addView(toolbarButton("Terminé") { finish() })
        return HorizontalScrollView(this).apply { addView(row) }
    }

    private fun buildOverlay() {
        val old = overlay
        if (old != null) root.removeView(old)
        val view: View = if (console == GameConsole.N64) {
            N64TouchOverlay(this, console.skin, { _, _ -> }) { _, _, _ -> }
        } else {
            TouchOverlay(this, console) { _, _ -> }
        }
        (view as EditableOverlay).apply {
            setEditMode(true)
            setSelectionListener { name ->
                selectedName = name
                updateInfo()
            }
        }
        root.addView(
            view, 0,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        )
        overlay = view
        updateInfo()
    }

    private fun groupLabel(name: String?): String = when (name) {
        "pad" -> "Croix"
        "face" -> "Boutons"
        "stick" -> "Stick"
        "c" -> "Boutons C"
        "start" -> "Start"
        "select" -> if (console == GameConsole.MD) "Mode" else "Select"
        "l" -> "L"
        "r" -> "R"
        "z" -> "Z"
        else -> "aucun"
    }

    private fun updateInfo() {
        val landscape = resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
        info.text = "Orientation : ${if (landscape) "paysage" else "portrait"}\n" +
            "Touche un élément puis glisse pour le déplacer.\n" +
            "Pince, ou utilise − et +, pour le redimensionner.\n" +
            "Sélection : ${groupLabel(selectedName)}"
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        updateInfo()
    }
}
