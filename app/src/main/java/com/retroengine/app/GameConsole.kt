package com.retroengine.app

class ButtonColors(val light: Int, val mid: Int, val dark: Int)

class ConsoleSkin(val a: ButtonColors, val b: ButtonColors)

private val RED = ButtonColors(0xFFFF7A7A.toInt(), 0xFFD32F2F.toInt(), 0xFF7F1414.toInt())
private val BLUE = ButtonColors(0xFF7AA8FF.toInt(), 0xFF1E5BD6.toInt(), 0xFF0F2F7A.toInt())
private val CHARCOAL = ButtonColors(0xFF8A8A94.toInt(), 0xFF4A4A55.toInt(), 0xFF1E1E26.toInt())

enum class GameConsole(
    val label: String,
    val coreLib: String,
    val romExt: String,
    val skin: ConsoleSkin
) {
    NES("NES", "libfceumm_libretro_android.so", "nes", ConsoleSkin(RED, RED)),
    GB("Game Boy", "libgambatte_libretro_android.so", "gb", ConsoleSkin(CHARCOAL, CHARCOAL)),
    GBC("Game Boy Color", "libgambatte_libretro_android.so", "gbc", ConsoleSkin(RED, BLUE));

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
