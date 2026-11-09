package com.retroengine.app

import android.app.AlertDialog
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.text.InputType
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import java.io.File

class GameDetailActivity : ComponentActivity() {

    private lateinit var library: GameLibrary
    private lateinit var gameId: String
    private lateinit var nameInput: EditText
    private lateinit var iconPreview: ImageView
    private lateinit var removeLogoButton: Button
    private var originalName = ""

    private val pickImage =
        registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            if (uri != null) {
                if (prepareSource(uri)) {
                    startActivity(
                        Intent(this, IconCropActivity::class.java).putExtra("gameId", gameId)
                    )
                } else {
                    Toast.makeText(this, "Image illisible.", Toast.LENGTH_LONG).show()
                }
            }
        }

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

    private fun prepareSource(uri: Uri): Boolean {
        return try {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            contentResolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it, null, bounds)
            }
            var sample = 1
            while (bounds.outWidth / sample > 1600 || bounds.outHeight / sample > 1600) sample *= 2
            val opts = BitmapFactory.Options().apply { inSampleSize = sample }
            val bmp = contentResolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it, null, opts)
            } ?: return false
            File(cacheDir, "icon_source.png").outputStream().use {
                bmp.compress(Bitmap.CompressFormat.PNG, 100, it)
            }
            true
        } catch (e: Exception) {
            false
        }
    }

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
            setPadding(0, dp(8), 0, dp(8))
        })

        // Logo
        column.addView(TextView(this).apply {
            text = "Logo du jeu"
            textSize = 14f
            setTextColor(0xFF9FA8FF.toInt())
            setPadding(0, dp(8), 0, dp(4))
        })
        iconPreview = ImageView(this).apply {
            scaleType = ImageView.ScaleType.FIT_CENTER
            background = GradientDrawable().apply {
                setColor(0xFF12132B.toInt())
                cornerRadius = dp(20).toFloat()
                setStroke(dp(1), 0x55FFFFFF)
            }
            clipToOutline = true
        }
        column.addView(
            iconPreview,
            LinearLayout.LayoutParams(dp(96), dp(96)).apply { bottomMargin = dp(8) }
        )

        val matchWidth = {
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }

        column.addView(Button(this).apply {
            text = "Choisir un logo"
            setOnClickListener {
                saveName()
                pickImage.launch(arrayOf("image/png", "image/jpeg"))
            }
        }, matchWidth())

        removeLogoButton = Button(this).apply {
            text = "Retirer le logo"
            setOnClickListener {
                library.iconFile(gameId).delete()
                refreshIcon()
            }
        }
        column.addView(removeLogoButton, matchWidth())

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
        }, matchWidth().apply { topMargin = dp(16) })

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
        refreshIcon()
    }

    override fun onResume() {
        super.onResume()
        if (::iconPreview.isInitialized) refreshIcon()
    }

    private fun refreshIcon() {
        val file = library.iconFile(gameId)
        if (file.exists()) {
            iconPreview.setImageBitmap(BitmapFactory.decodeFile(file.path))
            removeLogoButton.isEnabled = true
        } else {
            iconPreview.setImageDrawable(null)
            removeLogoButton.isEnabled = false
        }
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
                "La copie de la ROM, le logo et les sauvegardes de ce jeu seront supprimés de Retro Engine. " +
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
