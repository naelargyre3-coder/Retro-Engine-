package com.retroengine.app

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.ViewGroup
import android.view.ViewOutlineProvider
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import java.io.File

class IconCropActivity : ComponentActivity() {

    private lateinit var library: GameLibrary
    private var gameId = ""
    private var cropView: CropView? = null
    private val previews = ArrayList<ImageView>()

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

    private fun previewView(kind: Int, radiusDp: Int): ImageView = ImageView(this).apply {
        scaleType = ImageView.ScaleType.FIT_XY
        background = GradientDrawable().apply {
            shape = kind
            setColor(0xFF2A2B45.toInt())
            if (kind == GradientDrawable.RECTANGLE) cornerRadius = dp(radiusDp).toFloat()
        }
        outlineProvider = ViewOutlineProvider.BACKGROUND
        clipToOutline = true
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        library = GameLibrary(this)
        val id = intent.getStringExtra("gameId")
        val source = File(cacheDir, "icon_source.png")
        val bitmap = if (source.exists()) BitmapFactory.decodeFile(source.path) else null
        if (id == null || bitmap == null) {
            Toast.makeText(this, "Image introuvable.", Toast.LENGTH_LONG).show()
            finish()
            return
        }
        gameId = id

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(0xFF010317.toInt())
            setPadding(dp(16), dp(16), dp(16), dp(16))
        }

        root.addView(TextView(this).apply {
            text = "Cadrer le logo"
            textSize = 22f
            setTextColor(0xFFFFFFFF.toInt())
        })
        root.addView(TextView(this).apply {
            text = "Glisse pour déplacer, pince pour agrandir."
            textSize = 13f
            setTextColor(0xFF9FA8FF.toInt())
            setPadding(0, dp(4), 0, dp(8))
        })

        val crop = CropView(this, bitmap) { updatePreviews() }
        cropView = crop
        root.addView(crop, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))

        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(0, dp(12), 0, dp(12))
        }
        val shapes = listOf(
            previewView(GradientDrawable.OVAL, 0),
            previewView(GradientDrawable.RECTANGLE, 18),
            previewView(GradientDrawable.RECTANGLE, 8)
        )
        for (p in shapes) {
            previews.add(p)
            row.addView(
                p,
                LinearLayout.LayoutParams(dp(72), dp(72)).apply {
                    leftMargin = dp(10)
                    rightMargin = dp(10)
                }
            )
        }
        root.addView(row)

        val buttons = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        buttons.addView(Button(this).apply {
            text = "Annuler"
            setOnClickListener { finish() }
        }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        buttons.addView(Button(this).apply {
            text = "Valider"
            setOnClickListener { save() }
        }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        root.addView(buttons)

        ViewCompat.setOnApplyWindowInsetsListener(root) { v, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(
                bars.left + dp(16), bars.top + dp(16),
                bars.right + dp(16), bars.bottom + dp(16)
            )
            insets
        }
        setContentView(root)
    }

    private fun updatePreviews() {
        val bmp = cropView?.crop(192) ?: return
        for (p in previews) p.setImageBitmap(bmp)
    }

    private fun save() {
        val crop = cropView ?: return
        val out = crop.crop(512)
        try {
            library.iconFile(gameId).outputStream().use {
                out.compress(Bitmap.CompressFormat.PNG, 100, it)
            }
            finish()
        } catch (e: Exception) {
            Toast.makeText(this, "Impossible d'enregistrer le logo.", Toast.LENGTH_LONG).show()
        }
    }
}
