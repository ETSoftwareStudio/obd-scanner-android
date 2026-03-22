package studio.etsoftware.obdapp.data.logging

import org.junit.Assert.assertTrue
import org.junit.Test
import studio.etsoftware.obdapp.domain.model.DebugLogEntry
import studio.etsoftware.obdapp.domain.model.DebugLogType

class LogExportFormatterTest {
    private val formatter = LogExportFormatter()

    @Test
    fun `buildExportText includes header and entries`() {
        val logs =
            listOf(
                DebugLogEntry("10:00:00.100", DebugLogType.INFO, "Connected"),
                DebugLogEntry("10:00:01.100", DebugLogType.ERROR, "Read timeout"),
            )

        val output = formatter.buildExportText(logs, exportedAtMillis = 0L)

        assertTrue(output.contains("OBD Reader Debug Log Export"))
        assertTrue(output.contains("Entries: 2"))
        assertTrue(output.contains("10:00:00.100 [INFO] Connected"))
        assertTrue(output.contains("10:00:01.100 [ERROR] Read timeout"))
    }

    @Test
    fun `buildExportText escapes multiline messages`() {
        val logs =
            listOf(
                DebugLogEntry("11:11:11.111", DebugLogType.INFO, "line1\nline2"),
            )

        val output = formatter.buildExportText(logs)

        assertTrue(output.contains("11:11:11.111 [INFO] line1\\nline2"))
    }

    @Test
    fun `buildDefaultFileName returns expected pattern`() {
        val fileName = formatter.buildDefaultFileName(nowMillis = 0L)

        val pattern = Regex("""obd-debug-log-\d{4}-\d{2}-\d{2}-\d{6}\.txt""")
        assertTrue(pattern.matches(fileName))
    }
}
