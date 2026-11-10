package com.retroengine.app

enum class GameConsole(val label: String, val coreLib: String, val romExt: String) {
    NES("NES", "libfceumm_libretro_android.so", "nes"),
    GB("Game Boy", "libgambatte_libretro_android.so", "gb"),
    GBC("Game Boy Color", "libgambatte_libretro_android.so", "gbc");

    companion object {
        private val GB_LOGO = intArrayOf(0xCE, 0xED, 0x66, 0x66, 0xCC, 0x0D, 0x00, 0x0B)

        fun fromId(id: String?): GameConsole = values().firstOrNull { it.name == id } ?: NES

        fun fromLabel(label: String?): GameConsole =
            values().firstOrNull { it.label == label } ?: NES

        // Detecte la console d'apres l'en-tete de la ROM, ou null si le format est inconnu
        fun detect(header: ByteArray): GameConsole? {
            if (header.size >= 4 &&
                header[0] == 'N'.code.toByte() &&
                header[1] == 'E'.code.toByte() &&
                header[2] == 'S'.code.toByte() &&
                header[3] == 0x1A.toByte()
            ) return NES

            if (header.size >= 0x150) {
                var logoOk = true
                for (i in GB_LOGO.indices) {
                    if ((header[0x104 + i].toInt() and 0xFF) != GB_LOGO[i]) logoOk = false
                }
                if (logoOk) {
                    val cgbFlag = header[0x143].toInt() and 0xFF
                    return if (cgbFlag == 0x80 || cgbFlag == 0xC0) GBC else GB
                }
            }
            return null
        }
    }
}
