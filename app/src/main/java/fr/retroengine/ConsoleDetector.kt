package fr.retroengine

enum class ConsoleType(val displayName: String, vararg val extensions: String) {
    NES("NES", "nes"),
    SNES("Super Nintendo / SNES", "sfc", "smc"),
    GAME_BOY("Game Boy", "gb"),
    GAME_BOY_COLOR("Game Boy Color", "gbc"),
    GAME_BOY_ADVANCE("Game Boy Advance", "gba"),
    MEGA_DRIVE("Mega Drive / Genesis", "gen", "md", "smd"),
    NINTENDO_64("Nintendo 64", "n64", "z64", "v64"),
    UNKNOWN("Console non reconnue")
}
object ConsoleDetector {
    fun detect(fileName: String): ConsoleType {
        val extension = fileName.substringAfterLast('.', "").lowercase()
        return ConsoleType.values().firstOrNull {
            it != ConsoleType.UNKNOWN && extension in it.extensions
        } ?: ConsoleType.UNKNOWN
    }
}
