package com.retroengine.app

class ButtonColors(val light: Int, val mid: Int, val dark: Int)

class ConsoleSkin(
    val a: ButtonColors,
    val b: ButtonColors,
    val x: ButtonColors = a,
    val y: ButtonColors = b
)

enum class ButtonSet { TWO, TWO_SHOULDER, FOUR_SHOULDER }

private val RED = ButtonColors(0xFFFF7A7A.toInt(), 0xFFD32F2F.toInt(), 0xFF7F1414.toInt())
private val BLUE = ButtonColors(0xFF7AA8FF.toInt(), 0xFF1E5BD6.toInt(), 0xFF0F2F7A.toInt())
private val CHARCOAL = ButtonColors(0xFF8A8A94.toInt(), 0xFF4A4A55.toInt(), 0xFF1E1E26.toInt())
private val YELLOW = ButtonColors(0xFFFFE680.toInt(), 0xFFE0B010.toInt(), 0xFF8A6A00.toInt())
private val GREEN = ButtonColors(0xFF8CE08C.toInt(), 0xFF2E9E3E.toInt(), 0xFF145A20.toInt())
private val PURPLE = ButtonColors(0xFFC8A8FF.toInt(), 0xFF7A4FE0.toInt(), 0xFF35177F.toInt())
private val INDIGO = ButtonColors(0xFF9A9AFF.toInt(), 0xFF4A3FC0.toInt(), 0xFF1E1670.toInt())

enum class GameConsole(
    val label: String,
    val coreLib: String,
    val romExt: String,
    val buttonSet: ButtonSet,
    val skin: ConsoleSkin
) {
    NES("NES", "libfceumm_libretro_android.so", "nes", ButtonSet.TWO, ConsoleSkin(RED, RED)),
    GB("Game Boy", "libgambatte_libretro_android.so", "gb", ButtonSet.TWO, ConsoleSkin(CHARCOAL, CHARCOAL)),
    GBC("Game Boy Color", "libgambatte_libretro_android.so", "gbc", ButtonSet.TWO, ConsoleSkin(RED, BLUE)),
    SNES("SNES", "libsnes9x_libretro_android.so", "sfc", ButtonSet.FOUR_SHOULDER, ConsoleSkin(RED, YELLOW, BLUE, GREEN)),
    GBA("Game Boy Advance", "libmgba_libretro_android.so", "gba", ButtonSet.TWO_SHOULDER, ConsoleSkin(PURPLE, INDIGO));

    companion object {
        private val GB_LOGO = intArrayOf(0xCE, 0xED, 0x66, 0x66, 0xCC, 0x0D, 0x00, 0x0B)

        fun fromId(id: String?): GameConsole = values().firstOrNull { it.name == id } ?: NES

        fun fromLabel(label: String?): GameConsole =
            values().firstOrNull { it.label == label } ?: NES

        private fun u(h: ByteArray, i: Int): Int = h[i].toInt() and 0xFF

        private fun looksLikeSnes(h: ByteArray): Boolean {
            val bases = intArrayOf(0x7FDC, 0xFFDC)
            for (base in bases) {
                for (copier in intArrayOf(0, 512)) {
                    val o = base + copier
                    if (o + 4 <= h.size) {
                        val comp = u(h, o) or (u(h, o + 1) shl 8)
                        val sum = u(h, o + 2) or (u(h, o + 3) shl 8)
                        if (comp != 0 && sum != 0 && comp + sum == 0xFFFF) return true
                    }
                }
            }
            return false
        }

        // Detecte la console d'apres le contenu de la ROM, ou null si le format est inconnu
        fun detect(header: ByteArray): GameConsole? {
            if (header.size >= 4 &&
                header[0] == 'N'.code.toByte() &&
                header[1] == 'E'.code.toByte() &&
                header[2] == 'S'.code.toByte() &&
                header[3] == 0x1A.toByte()
            ) return NES

            if (header.size >= 0xB3 &&
                u(header, 0) == 0x24 && u(header, 1) == 0xFF &&
                u(header, 2) == 0xAE && u(header, 3) == 0x51 &&
                u(header, 0xB2) == 0x96
            ) return GBA

            if (header.size >= 0x150) {
                var logoOk = true
                for (i in GB_LOGO.indices) {
                    if (u(header, 0x104 + i) != GB_LOGO[i]) logoOk = false
                }
                if (logoOk) {
                    val cgbFlag = u(header, 0x143)
                    return if (cgbFlag == 0x80 || cgbFlag == 0xC0) GBC else GB
                }
            }

            if (looksLikeSnes(header)) return SNES
            return null
        }
    }
}
