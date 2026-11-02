package fr.retroengine

import org.junit.Assert.assertEquals
import org.junit.Test

class ConsoleDetectorTest {
    @Test fun detectsSupportedExtensions() {
        assertEquals(ConsoleType.NES, ConsoleDetector.detect("mario.nes"))
        assertEquals(ConsoleType.SNES, ConsoleDetector.detect("game.sfc"))
        assertEquals(ConsoleType.GAME_BOY, ConsoleDetector.detect("game.gb"))
        assertEquals(ConsoleType.GAME_BOY_COLOR, ConsoleDetector.detect("game.gbc"))
        assertEquals(ConsoleType.GAME_BOY_ADVANCE, ConsoleDetector.detect("game.gba"))
        assertEquals(ConsoleType.MEGA_DRIVE, ConsoleDetector.detect("game.md"))
        assertEquals(ConsoleType.NINTENDO_64, ConsoleDetector.detect("game.z64"))
    }
    @Test fun unknownExtensionStaysUnknown() {
        assertEquals(ConsoleType.UNKNOWN, ConsoleDetector.detect("readme.txt"))
        assertEquals(ConsoleType.UNKNOWN, ConsoleDetector.detect("no-extension"))
    }
}
