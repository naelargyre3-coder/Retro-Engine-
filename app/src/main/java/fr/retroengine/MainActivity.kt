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
import java.io.File
import java.io.FileOutputStream

class MainActivity : ComponentActivity() {
    private lateinit var resultText: TextView
    private lateinit var statusText: TextView
    private lateinit var coreText: TextView
    private lateinit var playButton: Button
    private var selectedRom: Uri? = null
    private var nesCoreLoaded = false

    private val romPicker = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) handleSelectedRom(uri)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = 0xFF0B1018.toInt()
        window.navigationBarColor = 0xFF0B1018.toInt()
        buildScreen()
        val coreStatus = NestopiaCoreLoader.load()
        nesCoreLoaded = coreStatus.loaded
        coreText.text = coreStatus.message
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
        playButton = Button(this).apply {
            text = "LANCER LE JEU NES"
            setTextColor(0xFFFFFFFF.toInt())
            setBackgroundColor(0xFF176B45.toInt())
            visibility = View.GONE
            setOnClickListener { selectedRom?.let { launchNesGame(it) } }
        }
        coreText = makeText("Vérification du moteur NES…", 14f, 0xFF9EADBF.toInt())
        statusText = makeText("En attente d'un fichier ROM.", 16f, 0xFFFFFFFF.toInt(), true)
        resultText = makeText(
            "Importe une ROM pour identifier sa console et afficher les informations du fichier.",
            14f, 0xFFB9C5D3.toInt()
        )
        val notice = makeText(
            "VERSION EXPÉRIMENTALE\nLe lancement NES utilise le cœur Nestopia et un frontend libretro Android. " +
                "Les autres consoles sont uniquement détectées pour le moment.",
            13f, 0xFFFFD58A.toInt()
        )
        page.addView(title)
        page.addView(subtitle, margin(4))
        page.addView(accent, LinearLayout.LayoutParams(-1, dp(3)).apply {
            topMargin = dp(22); bottomMargin = dp(22)
        })
        page.addView(coreText, margin(0))
        page.addView(importButton, margin(20).apply { height = dp(54) })
        page.addView(playButton, margin(10).apply { height = dp(54) })
        page.addView(statusText, margin(26))
        page.addView(resultText, margin(10))
        page.addView(notice, margin(34))
        setContentView(scroll)
    }

    private fun handleSelectedRom(uri: Uri) {
        selectedRom = uri
        try {
            contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        } catch (_: SecurityException) { }
        val details = queryFile(uri)
        val console = ConsoleDetector.detect(details.first)
        val nesInfo = if (console == ConsoleType.NES) {
            NesRomInspector.inspect(readHeader(uri), details.second)
        } else null
        val playable = console == ConsoleType.NES && nesInfo?.isAccepted == true && nesCoreLoaded
        playButton.visibility = if (playable) View.VISIBLE else View.GONE
        statusText.text = when {
            playable -> "ROM NES PRÊTE À ÊTRE LANCÉE"
            nesInfo?.isAccepted == false -> "ROM NES À VÉRIFIER"
            else -> "FICHIER IMPORTÉ"
        }
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
                if (playable) append("\n\nLe frontend NES est disponible pour cette version expérimentale.")
            } else if (console == ConsoleType.UNKNOWN) {
                append("\n\nCe format n'est pas reconnu par la détection actuelle.")
            } else {
                append("\n\nCette console est détectée, mais son moteur n'est pas encore disponible.")
            }
        }
    }

    private fun launchNesGame(uri: Uri) {
        try {
            val name = queryFile(uri).first.substringAfterLast('/').replace(Regex("[^A-Za-z0-9._-]"), "_")
            val directory = File(cacheDir, "roms").apply { mkdirs() }
            val destination = File(directory, if (name.endsWith(".nes", true)) name else "imported.nes")
            contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(destination).use { output -> input.copyTo(output) }
            } ?: throw IllegalStateException("Impossible d'ouvrir le fichier ROM.")
            if (destination.length() <= 16L) throw IllegalStateException("Le fichier ROM est incomplet.")
            startActivity(Intent(this, EmulatorActivity::class.java).putExtra("rom_path", destination.absolutePath))
        } catch (error: Exception) {
            statusText.text = "Impossible de préparer la ROM : ${error.message ?: "erreur inconnue"}"
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
