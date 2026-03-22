package studio.etsoftware.obdapp.data.repository

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import studio.etsoftware.obdapp.data.diagnostics.DtcParser

class DtcParserTest {
    private val parser = DtcParser()

    @Test
    fun `extracts standard trouble codes directly`() {
        assertEquals(listOf("P0301", "C1234"), parser.parse("43 P0301 C1234 P0000"))
    }

    @Test
    fun `returns empty list for no data responses`() {
        assertTrue(parser.parse("NO DATA").isEmpty())
        assertTrue(parser.parse("nodata").isEmpty())
    }

    @Test
    fun `decodes hexadecimal trouble code payloads`() {
        assertEquals(listOf("P010F", "U1000"), parser.parse("010F D000 0000"))
    }

    @Test
    fun `deduplicates repeated codes`() {
        assertEquals(listOf("P0301"), parser.parse("P0301 430301 P0301"))
    }
}
