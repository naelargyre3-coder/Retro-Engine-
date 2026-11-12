package com.retroengine.app

import android.app.AlertDialog
import android.graphics.Typeface
import android.os.Bundle
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import java.io.File
import java.util.Locale

class SettingsActivity : ComponentActivity() {

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

    private fun dirSize(f: File): Long {
        if (!f.exists()) return 0L
        if (f.isFile) return f.length()
        var total = 0L
        f.listFiles()?.forEach { total += dirSize(it) }
        return total
    }

    private fun formatSize(bytes: Long): String {
        val mb = bytes / 1048576.0
        return if (mb >= 1.0) {
            String.format(Locale.FRANCE, "%.1f Mo", mb)
        } else {
            String.format(Locale.FRANCE, "%d Ko", bytes / 1024)
        }
    }

    private fun heading(label: String): TextView = TextView(this).apply {
        text = label
        textSize = 18f
        typeface = Typeface.DEFAULT_BOLD
        setTextColor(0xFFFFFFFF.toInt())
        setPadding(0, dp(22), 0, dp(6))
    }

    private fun body(content: String): TextView = TextView(this).apply {
        text = content
        textSize = 14f
        setTextColor(0xFFB0B8FF.toInt())
        setLineSpacing(0f, 1.15f)
    }

    private fun button(label: String, onClick: () -> Unit): Button = Button(this).apply {
        text = label
        setOnClickListener { onClick() }
    }

    private fun matchWidth() = LinearLayout.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT,
        ViewGroup.LayoutParams.WRAP_CONTENT
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        render()
    }

    private fun versionName(): String = try {
        packageManager.getPackageInfo(packageName, 0).versionName ?: "?"
    } catch (e: Exception) {
        "?"
    }

    private fun render() {
        val library = GameLibrary(this)
        val gamesCount = library.all().size
        val gamesSize = dirSize(File(filesDir, "games")) + dirSize(File(filesDir, "saves"))
        val tempSize = dirSize(File(cacheDir, "generated")) + dirSize(File(cacheDir, "icon_source.png"))

        val column = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(20), dp(20), dp(20))
        }
        column.addView(TextView(this).apply {
            text = "Paramètres et confidentialité"
            textSize = 24f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(0xFFFFFFFF.toInt())
        })

        // Stockage
        column.addView(heading("Stockage"))
        column.addView(
            body(
                "Jeux : $gamesCount (ROM, logos et sauvegardes : ${formatSize(gamesSize)})\n" +
                    "APK générés (temporaires) : ${formatSize(tempSize)}"
            )
        )
        column.addView(button("Supprimer les APK générés") {
            File(cacheDir, "generated").deleteRecursively()
            File(cacheDir, "icon_source.png").delete()
            Toast.makeText(this, "APK générés supprimés.", Toast.LENGTH_SHORT).show()
            render()
        }, matchWidth().apply { topMargin = dp(8) })
        column.addView(button("Supprimer tous les jeux et sauvegardes") {
            AlertDialog.Builder(this)
                .setTitle("Tout supprimer ?")
                .setMessage(
                    "Les copies de ROM, les logos et les sauvegardes de tous tes jeux seront " +
                        "supprimés de Retro Engine. Les fichiers d'origine sur ton téléphone " +
                        "ne sont pas touchés. Cette action est définitive."
                )
                .setPositiveButton("Supprimer") { _, _ ->
                    val lib = GameLibrary(this)
                    for (g in lib.all()) lib.delete(g.id)
                    File(filesDir, "saves").deleteRecursively()
                    render()
                }
                .setNegativeButton("Annuler", null)
                .show()
        }, matchWidth())

        // Reglages
        column.addView(heading("Réglages"))
        column.addView(button("Rétablir graphismes et manettes par défaut") {
            GraphicsSettings.reset(this)
            val names = listOf(
                "controller_mapping", "controller_mapping_gba", "controller_mapping_snes",
                "controller_mapping_md", "controller_mapping_n64"
            )
            for (n in names) {
                getSharedPreferences(n, MODE_PRIVATE).edit().clear().apply()
            }
            Toast.makeText(this, "Réglages rétablis.", Toast.LENGTH_SHORT).show()
        }, matchWidth())

        // Signature
        column.addView(heading("Signature des APK"))
        val fingerprint = ApkSigning.fingerprint()
        column.addView(
            body(
                "Chaque APK de jeu est signé avec une clé créée sur ton téléphone et gardée dans " +
                    "le coffre de clés d'Android. Elle ne peut pas être exportée.\n\n" +
                    "Android n'accepte de mettre à jour une appli que si elle est signée avec la " +
                    "même clé. Si tu désinstalles Retro Engine, cette clé est perdue : les jeux " +
                    "déjà installés ne pourront plus être mis à jour par-dessus, il faudra les " +
                    "désinstaller avant d'installer une nouvelle version.\n\n" +
                    "Une signature prouve seulement l'origine du fichier. Elle ne garantit pas que " +
                    "son contenu est sûr."
            )
        )
        column.addView(TextView(this).apply {
            text = if (fingerprint != null) {
                "Empreinte SHA-256 :\n$fingerprint"
            } else {
                "Aucune clé créée pour l'instant : elle le sera à la première génération d'APK."
            }
            textSize = 11f
            typeface = Typeface.MONOSPACE
            setTextColor(0xFFFFD54F.toInt())
            setPadding(0, dp(8), 0, 0)
        })

        // Confidentialite
        column.addView(heading("Confidentialité"))
        column.addView(
            body(
                "Retro Engine fonctionne entièrement sur ton téléphone.\n\n" +
                    "• Aucun compte, aucune publicité, aucune télémétrie, aucun service de suivi.\n" +
                    "• L'appli n'a pas la permission d'accéder à Internet : elle ne peut rien envoyer " +
                    "(tu peux le vérifier dans les informations de l'appli, rubrique Autorisations).\n" +
                    "• Les ROM que tu ajoutes sont copiées dans l'espace privé de l'appli. Le fichier " +
                    "d'origine n'est pas modifié.\n" +
                    "• Données conservées sur l'appareil : copies des ROM, logos, sauvegardes de jeu, " +
                    "réglages de graphismes et de manettes. Elles restent jusqu'à ce que tu les " +
                    "supprimes (ici ou dans « Mon jeu ») ou que tu désinstalles Retro Engine.\n" +
                    "• La génération d'APK se fait sur le téléphone : aucun serveur n'est utilisé et " +
                    "rien n'est envoyé. Les APK générés sont gardés temporairement dans le cache.\n" +
                    "• Un APK de jeu contient la ROM : si tu le partages, c'est toi qui décides avec qui.\n" +
                    "• L'appli demande l'autorisation d'installer des applications, uniquement pour " +
                    "installer les jeux que tu génères.\n\n" +
                    "N'utilise que des ROM et des images dont tu as le droit de te servir. Aucun " +
                    "jeu, BIOS ni fichier protégé n'est fourni avec Retro Engine."
            )
        )

        // Licences
        column.addView(heading("Licences"))
        column.addView(
            body(
                "Retro Engine s'appuie sur des logiciels libres. D'après les fiches de ces projets " +
                    "(à revérifier avant toute distribution) :\n\n" +
                    "• LibretroDroid : GPL-3.0\n" +
                    "• FCEUmm (NES) : GPL-2.0\n" +
                    "• Gambatte (Game Boy, Color) : GPL-2.0\n" +
                    "• mGBA (GBA) : MPL-2.0\n" +
                    "• Snes9x (SNES) : licence non commerciale\n" +
                    "• Genesis Plus GX (Mega Drive) : licence non commerciale\n" +
                    "• Mupen64Plus-Next (N64) : GPL-2.0\n" +
                    "• apksig, AndroidX et Kotlin : Apache-2.0\n\n" +
                    "Conséquences : ne vends ni Retro Engine ni les APK qu'il génère. Si tu distribues " +
                    "l'appli ou un APK de jeu, le code source correspondant doit être publié, et les " +
                    "moteurs utilisés doivent être cités. Les logos et images que tu ajoutes restent " +
                    "soumis à leurs propres droits."
            )
        )

        // A propos
        column.addView(heading("À propos"))
        column.addView(body("Retro Engine, version ${versionName()}"))

        column.addView(button("Terminé") { finish() }, matchWidth().apply { topMargin = dp(20) })

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
