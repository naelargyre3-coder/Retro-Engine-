package com.retroengine.app

import android.graphics.Typeface
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.CheckBox
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat

class TouchSettingsActivity : ComponentActivity() {

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

    private fun heading(label: String): TextView = TextView(this).apply {
        text = label
        textSize = 18f
        typeface = Typeface.DEFAULT_BOLD
        setTextColor(0xFFFFFFFF.toInt())
        setPadding(0, dp(20), 0, dp(6))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        render()
    }

    private fun render() {
        val column = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(20), dp(20), dp(20))
        }
        column.addView(TextView(this).apply {
            text = "Options des touches"
            textSize = 24f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(0xFFFFFFFF.toInt())
        })
        column.addView(TextView(this).apply {
            text = "Les changements s'appliquent au prochain lancement d'un jeu."
            textSize = 14f
            setTextColor(0xFF9FA8FF.toInt())
        })

        // Affichage
        column.addView(heading("Affichage"))
        val currentMode = TouchStyle.mode(this)
        val modeGroup = RadioGroup(this)
        for (m in TouchStyle.Mode.values()) {
            val rb = RadioButton(this).apply {
                id = View.generateViewId()
                text = m.label
                setTextColor(0xFFFFFFFF.toInt())
                isChecked = (m == currentMode)
                setPadding(dp(8), dp(8), dp(8), dp(8))
                setOnClickListener { TouchStyle.setMode(this@TouchSettingsActivity, m) }
            }
            modeGroup.addView(rb)
        }
        column.addView(modeGroup)

        // Animations
        column.addView(CheckBox(this).apply {
            text = "Animer l'apparition et la disparition"
            setTextColor(0xFFFFFFFF.toInt())
            isChecked = TouchStyle.animations(this@TouchSettingsActivity)
            setOnCheckedChangeListener { _, checked ->
                TouchStyle.setAnimations(this@TouchSettingsActivity, checked)
            }
        })

        // Opacite
        column.addView(heading("Opacité"))
        val percentLabel = TextView(this).apply {
            text = "${TouchStyle.opacityPercent(this@TouchSettingsActivity)} %"
            textSize = 14f
            setTextColor(0xFFB0B8FF.toInt())
        }
        column.addView(percentLabel)
        column.addView(SeekBar(this).apply {
            max = 70
            progress = TouchStyle.opacityPercent(this@TouchSettingsActivity) - 30
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(bar: SeekBar?, value: Int, fromUser: Boolean) {
                    val percent = value + 30
                    percentLabel.text = "$percent %"
                    if (fromUser) TouchStyle.setOpacity(this@TouchSettingsActivity, percent)
                }

                override fun onStartTrackingTouch(bar: SeekBar?) {}

                override fun onStopTrackingTouch(bar: SeekBar?) {}
            })
        })

        // Couleurs
        column.addView(heading("Couleur des boutons"))
        val currentColor = TouchStyle.colorKey(this)
        val colorGroup = RadioGroup(this)
        for (c in TouchStyle.choices) {
            val rb = RadioButton(this).apply {
                id = View.generateViewId()
                text = c.label
                setTextColor(0xFFFFFFFF.toInt())
                isChecked = (c.key == currentColor)
                setPadding(dp(8), dp(8), dp(8), dp(8))
                setOnClickListener { TouchStyle.setColor(this@TouchSettingsActivity, c.key) }
            }
            colorGroup.addView(rb)
        }
        column.addView(colorGroup)

        val matchWidth = {
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }
        column.addView(Button(this).apply {
            text = "Réinitialiser ces options"
            setOnClickListener {
                TouchStyle.reset(this@TouchSettingsActivity)
                Toast.makeText(this@TouchSettingsActivity, "Options rétablies.", Toast.LENGTH_SHORT).show()
                render()
            }
        }, matchWidth().apply { topMargin = dp(16) })
        column.addView(Button(this).apply {
            text = "Terminé"
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
