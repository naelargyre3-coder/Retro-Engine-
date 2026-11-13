package com.retroengine.app

import android.app.AlertDialog
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class HistoryActivity : ComponentActivity() {

    private lateinit var container: LinearLayout

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

    private fun matchWidth() = LinearLayout.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT,
        ViewGroup.LayoutParams.WRAP_CONTENT
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val column = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(16), dp(16), dp(16))
        }
        column.addView(TextView(this).apply {
            text = "Historique des APK"
            textSize = 24f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(0xFFFFFFFF.toInt())
            setPadding(0, 0, 0, dp(8))
        })
        container = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        column.addView(container, matchWidth())

        column.addView(Button(this).apply {
            text = "Effacer l'historique"
            setOnClickListener {
                AlertDialog.Builder(this@HistoryActivity)
                    .setTitle("Effacer l'historique ?")
                    .setMessage("Les APK conservés seront aussi supprimés. Les jeux installés sur ton téléphone ne sont pas touchés.")
                    .setPositiveButton("Effacer") { _, _ ->
                        BuildHistory.clear(this@HistoryActivity)
                        refresh()
                    }
                    .setNegativeButton("Annuler", null)
                    .show()
            }
        }, matchWidth())
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

    override fun onResume() {
        super.onResume()
        refresh()
    }

    private fun smallButton(label: String, onClick: () -> Unit): Button = Button(this).apply {
        text = label
        textSize = 12f
        setOnClickListener { onClick() }
    }

    private fun buttonRow(vararg buttons: Button): LinearLayout =
        LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            for (b in buttons) {
                addView(b, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            }
        }

    private fun refresh() {
        container.removeAllViews()
        val records = BuildHistory.all(this)
        if (records.isEmpty()) {
            container.addView(TextView(this).apply {
                text = "Aucun APK généré pour l'instant. Ouvre un jeu (« Modifier ») puis « Générer l'APK du jeu »."
                textSize = 14f
                setTextColor(0xFF9FA8FF.toInt())
                setPadding(0, dp(4), 0, dp(16))
            })
            return
        }
        for (r in records) container.addView(card(r), matchWidth().apply { bottomMargin = dp(10) })
    }

    private fun card(r: BuildRecord): View {
        val fmt = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.FRANCE)
        val apk = BuildHistory.apkFile(this, r)

        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), dp(10), dp(14), dp(10))
            background = GradientDrawable().apply {
                setColor(0xFF12132B.toInt())
                cornerRadius = dp(14).toFloat()
                setStroke(dp(1), 0x55FFFFFF)
            }
        }
        box.addView(TextView(this).apply {
            text = "${r.gameName} — version ${r.versionName}"
            textSize = 17f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(0xFFFFFFFF.toInt())
        })
        val size = if (r.ok) " · ${r.sizeKb / 1024.0}".take(0) else ""
        box.addView(TextView(this).apply {
            text = "${r.console} · ${fmt.format(Date(r.id))}$size" +
                if (r.ok) " · ${r.sizeKb} Ko" else ""
            textSize = 13f
            setTextColor(0xFFB0B8FF.toInt())
        })
        box.addView(TextView(this).apply {
            text = r.pkg
            textSize = 11f
            typeface = Typeface.MONOSPACE
            setTextColor(0xFF8A90C0.toInt())
        })
        box.addView(TextView(this).apply {
            text = when {
                !r.ok -> "Échec"
                apk == null -> "Réussi · APK supprimé"
                else -> "Réussi · signature vérifiée"
            }
            textSize = 13f
            setTextColor(if (r.ok) 0xFF7CE08C.toInt() else 0xFFFF7A7A.toInt())
            setPadding(0, dp(4), 0, dp(4))
        })

        if (r.ok && apk != null) {
            box.addView(buttonRow(
                smallButton("Installer") { GameApkBuilder.install(this, apk) },
                smallButton("Partager") { GameApkFlow.share(this, apk) },
                smallButton("Supprimer l'APK") {
                    BuildHistory.deleteApk(this, r)
                    refresh()
                }
            ))
        }
        if (!r.ok) {
            box.addView(buttonRow(
                smallButton("Voir l'erreur") {
                    AlertDialog.Builder(this)
                        .setTitle("Erreur de génération")
                        .setMessage(r.message)
                        .setPositiveButton("OK", null)
                        .show()
                }
            ))
        }
        val gameExists = GameLibrary(this).get(r.gameId) != null
        val second = ArrayList<Button>()
        if (gameExists) {
            second.add(smallButton("Régénérer") {
                GameApkFlow.generate(this, r.gameId) { refresh() }
            })
        }
        second.add(smallButton("Retirer de la liste") {
            BuildHistory.remove(this, r.id)
            Toast.makeText(this, "Retiré de l'historique.", Toast.LENGTH_SHORT).show()
            refresh()
        })
        box.addView(buttonRow(*second.toTypedArray()))
        return box
    }
}
