package com.retroengine.app

import android.app.AlertDialog
import android.content.Intent
import android.graphics.Typeface
import android.os.Bundle
import android.text.InputType
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat

class GameDetailActivity : ComponentActivity() {

    private lateinit var library: GameLibrary
    private lateinit var gameId: String
    private lateinit var nameInput: EditText
    private var originalName = ""

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        library = GameLibrary(this)
        val id = intent.getStringExtra("gameId")
        val game = if (id != null) library.get(id) else null
        if (id == null || game == null) {
            finish()
            return
        }
        gameId = id
        originalName = game.name

        val column = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(20), dp(20), dp(20))
        }
        column.addView(TextView(this).apply {
            text = "Mon jeu"
            textSize = 24f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(0xFFFFFFFF.toInt())
        })
        column.addView(TextView(this).apply {
            text = "Nom du jeu"
            textSize = 14f
            setTextColor(0xFF9FA8FF.toInt())
            setPadding(0, dp(16), 0, dp(4))
        })
        nameInput = EditText(this).apply {
            setText(game.name)
            setSingleLine(true)
            inputType = InputType.TYPE_CLASS_TEXT
            setTextColor(0xFFFFFFFF.toInt())
            setHintTextColor(0xFF777777.toInt())
            hint = "Nom du jeu"
        }
        column.addView(nameInput)
        column.addView(TextView(this).apply {
            text = "Console : ${game.console}"
            textSize = 14f
            setTextColor(0xFFB0B8FF.toInt())
            setPadding(0, dp(8), 0, dp(16))
        })

        val matchWidth = {
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }

        column.addView(Button(this).apply {
            text = "Jouer"
            setOnClickListener {
                saveName()
                startActivity(
                    Intent(this@GameDetailActivity, GameActivity::class.java)
                        .putExtra("gameId", gameId)
                )
                finish()
            }
        }, matchWidth())

        column.addView(Button(this).apply {
            text = "Supprimer le jeu"
            setOnClickListener { confirmDelete() }
        }, matchWidth())

        column.addView(Button(this).apply {
            text = "Terminé"
            setOnClickListener {
                saveName()
                finish()
            }
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

    private fun saveName() {
        if (!::nameInput.isInitialized) return
        val newName = nameInput.text.toString().trim()
        if (newName.isNotEmpty() && newName != originalName) {
            library.rename(gameId, newName)
            originalName = newName
        }
    }

    override fun onPause() {
        saveName()
        super.onPause()
    }

    private fun confirmDelete() {
        AlertDialog.Builder(this)
            .setTitle("Supprimer ce jeu ?")
            .setMessage(
                "La copie de la ROM et les sauvegardes de ce jeu seront supprimées de Retro Engine. " +
                    "Le fichier d'origine sur ton téléphone n'est pas touché."
            )
            .setPositiveButton("Supprimer") { _, _ ->
                library.delete(gameId)
                finish()
            }
            .setNegativeButton("Annuler", null)
            .show()
    }
}
