package fr.retroengine

data class NesRomInfo(
    val isAccepted: Boolean,
    val format: String,
    val mapper: Int?,
    val prgRomBytes: Long?,
    val chrRomBytes: Long?,
    val message: String
)

/** Header inspection only; this does not emulate or launch a game. */
object NesRomInspector {
    private const val HEADER_SIZE = 16
    private const val PRG_BANK_SIZE = 16 * 1024L
    private const val CHR_BANK_SIZE = 8 * 1024L
    private val MAGIC = byteArrayOf(0x4E, 0x45, 0x53, 0x1A)

    fun inspect(header: ByteArray, fileSize: Long): NesRomInfo {
        if (header.size < HEADER_SIZE) return rejected("Header incomplet : 16 octets sont nécessaires.")
        if (!MAGIC.indices.all { header[it] == MAGIC[it] }) {
            return rejected("Signature iNES invalide (NES suivi de 0x1A attendu).")
        }

        val flags6 = header[6].toInt() and 0xFF
        val flags7 = header[7].toInt() and 0xFF
        val isNes2 = (flags7 and 0x0C) == 0x08
        val format = if (isNes2) "NES 2.0" else "iNES"
        val prgBanks = header[4].toInt() and 0xFF
        val chrBanks = header[5].toInt() and 0xFF
        val trainerBytes = if ((flags6 and 0x04) != 0) 512L else 0L
        val mapperLow = (flags6 ushr 4) or (flags7 and 0xF0)
        val mapper = if (isNes2) mapperLow or ((header[8].toInt() and 0x0F) shl 8) else mapperLow

        if (prgBanks == 0 && !isNes2) {
            return rejected("Taille PRG-ROM nulle dans un en-tête iNES classique.", format)
        }

        val prgBytes = prgBanks * PRG_BANK_SIZE
        val chrBytes = chrBanks * CHR_BANK_SIZE
        if (isNes2) {
            return NesRomInfo(
                true, format, mapper, prgBytes, chrBytes,
                "En-tête reconnu. Le décodage avancé des tailles NES 2.0 reste à ajouter."
            )
        }

        val minimumSize = HEADER_SIZE + trainerBytes + prgBytes + chrBytes
        if (fileSize >= 0 && fileSize < minimumSize) {
            return NesRomInfo(
                false, format, mapper, prgBytes, chrBytes,
                "Fichier tronqué : au moins " + minimumSize + " octets sont attendus."
            )
        }
        return NesRomInfo(true, format, mapper, prgBytes, chrBytes, "En-tête reconnu et taille minimale cohérente.")
    }

    private fun rejected(message: String, format: String = "inconnu") =
        NesRomInfo(false, format, null, null, null, message)
}
