package com.retroengine.app

import android.content.Intent
import android.graphics.BitmapFactory
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat

class MainActivity : ComponentActivity() {

    private lateinit var library: GameLibrary
    private lateinit var listContainer: LinearLayout

    private val pickRom =
        registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            if (uri != null) importRom(uri)
        }

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

    private fun matchWidth() = LinearLayout.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT,
        ViewGroup.LayoutParams.WRAP_CONTENT
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        library = GameLibrary(this)

        val column = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(16), dp(16), dp(16))
        }

        val logo = ImageView(this).apply {
            setImageResource(R.drawable.retro_logo)
            scaleType = ImageView.ScaleType.FIT_CENTER
        }
        column.addView(
            logo,
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(140))
        )

        column.addView(TextView(this).apply {
            text = "Mes jeux"
            textSize = 22f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(0xFFFFFFFF.toInt())
            setPadding(0, dp(12), 0, dp(8))
        })

        listContainer = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        column.addView(listContainer, matchWidth())

        column.addView(Button(this).apply {
            text = "Ajouter un jeu (ROM NES)"
            setOnClickListener { pickRom.launch(arrayOf("*/*")) }
        }, matchWidth())

        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        row.addView(Button(this).apply {
            text = "Manette"
            setOnClickListener {
                startActivity(Intent(this@MainActivity, ControllerConfigActivity::class.java))
            }
        }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        row.addView(Button(this).apply {
            text = "Graphismes"
            setOnClickListener {
                startActivity(Intent(this@MainActivity, GraphicsConfigActivity::class.java))
            }
        }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        column.addView(row, matchWidth())

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

    override fun onResume() {
        super.onResume()
        refresh()
    }

    private fun refresh() {
        listContainer.removeAllViews()
        val games = library.all()
        if (games.isEmpty()) {
            listContainer.addView(TextView(this).apply {
                text = "Aucun jeu pour l'instant. Ajoute une ROM NES pour commencer."
                textSize = 14f
                setTextColor(0xFF9FA8FF.toInt())
                setPadding(0, dp(4), 0, dp(16))
            })
            return
        }
        for (game in games) listContainer.addView(gameCard(game))
    }

    private fun gameCard(game: GameEntry): View {
        val title = TextView(this).apply {
            text = game.name
            textSize = 18f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(0xFFFFFFFF.toInt())
        }
        val subtitle = TextView(this).apply {
            text = if (library.hasSave(game.id)) "${game.console} · Sauvegarde" else game.console
            textSize = 13f
            setTextColor(0xFFB0B8FF.toInt())
        }
                val iconView = ImageView(this).apply {
            val file = library.iconFile(game.id)
            if (file.exists()) setImageBitmap(BitmapFactory.decodeFile(file.path))
            scaleType = ImageView.ScaleType.FIT_CENTER
            background = GradientDrawable().apply {
                setColor(0xFF000000.toInt())
                cornerRadius = dp(12).toFloat()
            }
            clipToOutline = true
                }
        val texts = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(title)
            addView(subtitle)
        }
        val edit = Button(this).apply {
            text = "Modifier"
            setOnClickListener {
                startActivity(
                    Intent(this@MainActivity, GameDetailActivity::class.java)
                        .putExtra("gameId", game.id)
                )
            }
        }
        val background = GradientDrawable().apply {
            setColor(0xFF12132B.toInt())
            cornerRadius = dp(14).toFloat()
            setStroke(dp(1), 0x55FFFFFF)
        }
        return LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(14), dp(10), dp(10), dp(10))
            this.background = background
            addView(iconView, LinearLayout.LayoutParams(dp(56), dp(56)).apply { rightMargin = dp(12) })
            addView(texts, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            addView(edit)
            setOnClickListener {
                startActivity(
                    Intent(this@MainActivity, GameActivity::class.java)
                        .putExtra("gameId", game.id)
                )
            }
            layoutParams = matchWidth().apply { bottomMargin = dp(10) }
        }
    }

    private fun importRom(uri: Uri) {
        try {
            val entry = library.import(uri)
            startActivity(
                Intent(this, GameDetailActivity::class.java).putExtra("gameId", entry.id)
            )
        } catch (e: Exception) {
            Toast.makeText(this, e.message ?: "Importation impossible.", Toast.LENGTH_LONG).show()
        }
    }
}
