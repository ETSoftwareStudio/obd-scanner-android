package studio.etsoftware.obdapp.util

import org.junit.Assert.assertTrue
import org.junit.Test

class LogExportFormatterTest {
    private val formatter = LogExportFormatter()

    @Test
    fun `buildExportText includes header and entries`() {
        val logs =
            listOf(
                LogEntry("10:00:00.100", LogType.INFO, "Connected"),
                LogEntry("10:00:01.100", LogType.ERROR, "Read timeout"),
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
                LogEntry("11:11:11.111", LogType.INFO, "line1\nline2"),
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
