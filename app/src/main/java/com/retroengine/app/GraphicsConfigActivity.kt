package com.retroengine.app

import android.graphics.Typeface
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.ScrollView
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat

class GraphicsConfigActivity : ComponentActivity() {

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        render()
    }

    private fun heading(label: String, size: Float): TextView = TextView(this).apply {
        text = label
        textSize = size
        typeface = Typeface.DEFAULT_BOLD
        setTextColor(0xFFFFFFFF.toInt())
        setPadding(0, dp(16), 0, dp(6))
    }

    private fun render() {
        val column = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(20), dp(20), dp(20))
        }

        column.addView(heading("Param\u00e8tres graphiques", 24f))
        column.addView(TextView(this).apply {
            text = "Les changements s'appliquent au prochain lancement d'un jeu."
            textSize = 14f
            setTextColor(0xFF9FA8FF.toInt())
        })

        // Filtres
        column.addView(heading("Filtre", 18f))
        val currentFilter = GraphicsSettings.filter(this)
        val filterGroup = RadioGroup(this)
        for (f in GameFilter.values()) {
            val rb = RadioButton(this).apply {
                id = View.generateViewId()
                text = if (f.available) {
                    "${f.label}\n${f.description}"
                } else {
                    "${f.label} (indisponible)\n${f.note}"
                }
                setTextColor(if (f.available) 0xFFFFFFFF.toInt() else 0xFF777777.toInt())
                isEnabled = f.available
                isChecked = (f == currentFilter)
                setPadding(dp(8), dp(8), dp(8), dp(8))
                setOnClickListener { GraphicsSettings.setFilter(this@GraphicsConfigActivity, f) }
            }
            filterGroup.addView(rb)
        }
        column.addView(filterGroup)

        // Orientation
        column.addView(heading("Orientation", 18f))
        val currentMode = GraphicsSettings.orientation(this)
        val modeGroup = RadioGroup(this)
        for (m in OrientationMode.values()) {
            val rb = RadioButton(this).apply {
                id = View.generateViewId()
                text = m.label
                setTextColor(0xFFFFFFFF.toInt())
                isChecked = (m == currentMode)
                setPadding(dp(8), dp(8), dp(8), dp(8))
                setOnClickListener { GraphicsSettings.setOrientation(this@GraphicsConfigActivity, m) }
            }
            modeGroup.addView(rb)
        }
        column.addView(modeGroup)

        val matchWidth = {
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }

        column.addView(Button(this).apply {
            text = "Param\u00e8tres par d\u00e9faut"
            setOnClickListener {
                GraphicsSettings.reset(this@GraphicsConfigActivity)
                render()
            }
        }, matchWidth().apply { topMargin = dp(16) })

        column.addView(Button(this).apply {
            text = "Termin\u00e9"
            setOnClickListener { finish() }
        }, matchWidth())

        val scroll = ScrollView(this).apply {
            setBackgroundColor(0xFF010317.toInt())
            addView(column)
        }
        ViewCompat.setOnApplyWindowInsetsListener(scroll) { v, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            insets
        }
        setContentView(scroll)
    }
}
