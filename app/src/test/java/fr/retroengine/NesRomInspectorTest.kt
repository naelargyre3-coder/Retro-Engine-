package fr.retroengine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NesRomInspectorTest {
    private fun header(prg: Int = 1, chr: Int = 1, flags6: Int = 0, flags7: Int = 0): ByteArray =
        byteArrayOf(0x4E, 0x45, 0x53, 0x1A, prg.toByte(), chr.toByte(),
            flags6.toByte(), flags7.toByte(), 0, 0, 0, 0, 0, 0, 0, 0)

    @Test fun acceptsValidInesHeaderAndPayloadSize() {
        val info = NesRomInspector.inspect(header(), 16 + 16_384 + 8_192)
        assertTrue(info.isAccepted)
        assertEquals("iNES", info.format)
        assertEquals(0, info.mapper)
    }

    @Test fun rejectsInvalidMagic() {
        val badHeader = header().also { it[0] = 0 }
        assertFalse(NesRomInspector.inspect(badHeader, 24_592).isAccepted)
    }

    @Test fun rejectsTruncatedRom() {
        assertFalse(NesRomInspector.inspect(header(), 16 + 16_384).isAccepted)
    }

    @Test fun accountsForTrainerAndMapper() {
        val info = NesRomInspector.inspect(header(flags6 = 0x14), 16 + 512 + 16_384 + 8_192)
        assertTrue(info.isAccepted)
        assertEquals(1, info.mapper)
    }

    @Test fun identifiesNes20WithoutClaimingFullSizeValidation() {
        val info = NesRomInspector.inspect(header(flags7 = 0x08), 16)
        assertTrue(info.isAccepted)
        assertEquals("NES 2.0", info.format)
        assertTrue(info.message.contains("NES 2.0"))
    }

    @Test fun rejectsShortHeader() {
        assertFalse(NesRomInspector.inspect(byteArrayOf(0x4E, 0x45), 2).isAccepted)
    }
}
