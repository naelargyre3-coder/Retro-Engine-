package fr.retroengine

import androidx.activity.ComponentActivity
import android.content.Intent
import android.database.Cursor
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts

class MainActivity : ComponentActivity() {
    private lateinit var resultText: TextView
    private lateinit var statusText: TextView
    private lateinit var coreText: TextView
    private val romPicker = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) handleSelectedRom(uri)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = 0xFF0B1018.toInt()
        window.navigationBarColor = 0xFF0B1018.toInt()
        buildScreen()
        coreText.text = NestopiaCoreLoader.load().message
    }

    private fun buildScreen() {
        val page = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(22), dp(26), dp(22), dp(26))
            setBackgroundColor(0xFF0B1018.toInt())
        }
        val scroll = ScrollView(this).apply { addView(page) }
        val title = makeText("RETRO ENGINE", 30f, 0xFFFFFFFF.toInt(), true)
        val subtitle = makeText("Atelier de création de jeux Android", 15f, 0xFF9EADBF.toInt())
        val accent = View(this).apply { setBackgroundColor(0xFF00D9FF.toInt()) }
        val importButton = Button(this).apply {
            text = "IMPORTER UNE ROM"
            setTextColor(0xFF071018.toInt())
            setBackgroundColor(0xFF00D9FF.toInt())
            setOnClickListener { romPicker.launch(arrayOf("*/*")) }
        }
        coreText = makeText("Vérification du moteur NES…", 14f, 0xFF9EADBF.toInt())
        statusText = makeText("En attente d'un fichier ROM.", 16f, 0xFFFFFFFF.toInt(), true)
        resultText = makeText(
            "Importe une ROM pour identifier sa console et afficher les informations du fichier.",
            14f, 0xFFB9C5D3.toInt()
        )
        val notice = makeText(
            "ÉTAT DE CETTE VERSION\nL'import local et la détection par extension sont disponibles. " +
                "Le cœur natif NES est intégré, mais le frontend libretro Android n'est pas encore connecté. " +
                "Aucun jeu ne peut donc être lancé pour le moment.",
            13f, 0xFFFFD58A.toInt()
        )
        page.addView(title)
        page.addView(subtitle, margin(4))
        page.addView(accent, LinearLayout.LayoutParams(-1, dp(3)).apply {
            topMargin = dp(22); bottomMargin = dp(22)
        })
        page.addView(coreText, margin(0))
        page.addView(importButton, margin(20).apply { height = dp(54) })
        page.addView(statusText, margin(26))
        page.addView(resultText, margin(10))
        page.addView(notice, margin(34))
        setContentView(scroll)
    }

    private fun handleSelectedRom(uri: Uri) {
        try {
            contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        } catch (_: SecurityException) {
            // Some document providers do not support persistent URI permissions.
        }
        val details = queryFile(uri)
        val console = ConsoleDetector.detect(details.first)
        val nesInfo = if (console == ConsoleType.NES) {
            NesRomInspector.inspect(readHeader(uri), details.second)
        } else null
        statusText.text = if (nesInfo?.isAccepted == false) "ROM NES À VÉRIFIER" else "FICHIER IMPORTÉ"
        resultText.text = buildString {
            append("Nom : ").append(details.first)
            append("\nTaille : ").append(if (details.second >= 0) details.second.toString() + " octets" else "indisponible")
            append("\nConsole détectée : ").append(console.displayName)
            if (nesInfo != null) {
                append("\n\nAnalyse NES : ").append(nesInfo.message)
                if (nesInfo.mapper != null) append("\nMapper : ").append(nesInfo.mapper)
                if (nesInfo.prgRomBytes != null) append("\nPRG-ROM : ").append(nesInfo.prgRomBytes).append(" octets")
                if (nesInfo.chrRomBytes != null) append("\nCHR-ROM : ").append(nesInfo.chrRomBytes).append(" octets")
                append("\nFormat : ").append(nesInfo.format)
                append("\n\nLe cœur NES est chargé séparément ; le frontend nécessaire au lancement n'est pas encore disponible.")
            } else if (console == ConsoleType.UNKNOWN) {
                append("\n\nCe format n'est pas reconnu par la détection actuelle.")
            } else {
                append("\n\nLe fichier est identifié. Le lancement sera disponible lorsque le moteur de cette console sera intégré.")
            }
        }
    }

    private fun readHeader(uri: Uri): ByteArray {
        val header = ByteArray(16)
        contentResolver.openInputStream(uri)?.use { input ->
            var offset = 0
            while (offset < header.size) {
                val count = input.read(header, offset, header.size - offset)
                if (count <= 0) break
                offset += count
            }
            if (offset < header.size) return header.copyOf(offset)
        }
        return header
    }

    private fun queryFile(uri: Uri): Pair<String, Long> {
        var name = uri.lastPathSegment ?: "ROM"
        var size = -1L
        val cursor: Cursor? = contentResolver.query(uri, null, null, null, null)
        cursor?.use {
            val nameIndex = it.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            val sizeIndex = it.getColumnIndex(OpenableColumns.SIZE)
            if (it.moveToFirst()) {
                if (nameIndex >= 0) name = it.getString(nameIndex) ?: name
                if (sizeIndex >= 0 && !it.isNull(sizeIndex)) size = it.getLong(sizeIndex)
            }
        }
        return name to size
    }

    private fun makeText(value: String, size: Float, color: Int, bold: Boolean = false) =
        TextView(this).apply {
            text = value
            textSize = size
            setTextColor(color)
            if (bold) setTypeface(typeface, android.graphics.Typeface.BOLD)
            gravity = Gravity.START
        }

    private fun margin(top: Int) = LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(top) }
    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()
}
