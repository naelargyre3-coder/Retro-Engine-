package com.retroengine.app

import android.graphics.Typeface
import android.os.Bundle
import android.view.Gravity
import android.view.KeyEvent
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat

class ControllerConfigActivity : ComponentActivity() {

    private var set = ButtonSet.TWO
    private lateinit var mapping: ControllerMapping
    private lateinit var status: TextView
    private lateinit var lastPressed: TextView
    private var waiting: PadAction? = null
    private val valueViews = HashMap<PadAction, TextView>()
    private val actionButtons = HashMap<PadAction, Button>()

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        render()
    }

    private fun selectorButton(label: String, target: ButtonSet): Button = Button(this).apply {
        text = label
        isEnabled = (set != target)
        setOnClickListener {
            set = target
            render()
        }
    }

    private fun render() {
        mapping = ControllerMapping(this, set)
        waiting = null
        valueViews.clear()
        actionButtons.clear()

        val column = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(20), dp(20), dp(20))
        }
        column.addView(TextView(this).apply {
            text = "Configurer la manette"
            textSize = 24f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(0xFFFFFFFF.toInt())
        })

        val selector = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        val selectorParams = { LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f) }
        selector.addView(selectorButton("NES / GB", ButtonSet.TWO), selectorParams())
        selector.addView(selectorButton("GBA", ButtonSet.TWO_SHOULDER), selectorParams())
        selector.addView(selectorButton("SNES", ButtonSet.FOUR_SHOULDER), selectorParams())
        selector.addView(selectorButton("MD", ButtonSet.SIX), selectorParams())
        column.addView(
            selector,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dp(8) }
        )

        status = TextView(this).apply {
            textSize = 14f
            setTextColor(0xFF9FA8FF.toInt())
            setPadding(0, dp(8), 0, 0)
        }
        column.addView(status)

        lastPressed = TextView(this).apply {
            textSize = 14f
            setTextColor(0xFFFFD54F.toInt())
            setPadding(0, dp(4), 0, dp(12))
            text = "Test : appuie sur un bouton de ta manette"
        }
        column.addView(lastPressed)

        for (action in mapping.actionList()) addRow(column, action)

        val matchWidth = {
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }

        column.addView(Button(this).apply {
            text = "Inverser A / B"
            setOnClickListener {
                waiting = null
                mapping.swapAB()
                refresh()
            }
        }, matchWidth())

        column.addView(Button(this).apply {
            text = "R\u00e9initialiser"
            setOnClickListener {
                waiting = null
                mapping.reset()
                refresh()
            }
        }, matchWidth())

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
        refresh()
    }

    override fun onResume() {
        super.onResume()
        if (::mapping.isInitialized) refresh()
    }

    private fun addRow(parent: LinearLayout, action: PadAction) {
        val label = TextView(this).apply {
            text = action.label
            textSize = 18f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(0xFFFFFFFF.toInt())
        }
        val value = TextView(this).apply {
            textSize = 14f
            setTextColor(0xFFB0B8FF.toInt())
        }
        val left = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(label)
            addView(value)
        }
        val button = Button(this).apply {
            text = "Changer"
            setOnClickListener {
                waiting = if (waiting == action) null else action
                refresh()
            }
        }
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(6), 0, dp(6))
            addView(
                left,
                LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            )
            addView(button)
        }
        parent.addView(row)
        valueViews[action] = value
        actionButtons[action] = button
    }

    private fun refresh() {
        status.text = if (hasGamepad()) {
            "Manette d\u00e9tect\u00e9e"
        } else {
            "Aucune manette d\u00e9tect\u00e9e : branche ou connecte une manette"
        }
        for (action in mapping.actionList()) {
            valueViews[action]?.text =
                if (waiting == action) "Appuie sur un bouton de la manette\u2026" else mapping.describe(action)
            actionButtons[action]?.text = if (waiting == action) "Annuler" else "Changer"
        }
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (isGamepadSource(event.source)) {
            if (event.action == KeyEvent.ACTION_DOWN && event.repeatCount == 0) {
                val name = padButtonName(event.keyCode)
                val target = waiting
                if (target != null) {
                    mapping.assign(target, event.keyCode)
                    waiting = null
                    lastPressed.text = "Assign\u00e9 : $name \u2192 ${target.label}"
                    refresh()
                } else {
                    val action = mapping.lookup(event.keyCode)
                    lastPressed.text = if (action != null) {
                        "Test : $name \u2192 ${action.label}"
                    } else {
                        "Test : $name (non assign\u00e9)"
                    }
                }
            }
            return true
        }
        return super.dispatchKeyEvent(event)
    }
}
