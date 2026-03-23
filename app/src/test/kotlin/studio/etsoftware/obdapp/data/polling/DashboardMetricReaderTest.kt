package studio.etsoftware.obdapp.data.polling

import com.github.eltonvs.obd.command.ObdRawResponse
import com.github.eltonvs.obd.command.ObdResponse
import com.github.eltonvs.obd.command.engine.SpeedCommand
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import studio.etsoftware.obdapp.data.logging.LogManager
import studio.etsoftware.obdapp.data.session.ObdCommandSession
import studio.etsoftware.obdapp.data.session.ObdSessionDataSource

class DashboardMetricReaderTest {
    private val logManager = mockk<LogManager>(relaxed = true)
    private val metricsStore = mockk<DashboardMetricsStore>(relaxed = true)
    private val sessionDataSource = mockk<ObdSessionDataSource>()
    private val session = mockk<ObdCommandSession>()
    private val reader = DashboardMetricReader(logManager, metricsStore, sessionDataSource)

    @Test
    fun `readDueMetrics publishes successful metric values`() =
        runTest {
            val processed = mutableListOf<DashboardMetricId>()
            val speedResponse = response(SpeedCommand(), value = "42", rawValue = "41 0D 2A", unit = "kmh")

            coEvery {
                sessionDataSource.withConnectedSession<Triple<ObdResponse, String, String>>(any())
            } coAnswers {
                firstArg<suspend (ObdCommandSession) -> Result<Triple<ObdResponse, String, String>>>().invoke(session)
            }
            coEvery {
                session.run(studio.etsoftware.obdapp.domain.model.TelemetryContext.DASHBOARD, 7L, match { it is SpeedCommand }, any())
            } returns speedResponse
            every { session.previewValue("41 0D 2A") } returns "41 0D 2A"

            val stats =
                reader.readDueMetrics(7L, listOf(DashboardMetricId.SPEED)) {
                    processed += it
                }

            assertEquals(1, stats.commandCount)
            assertEquals(1, stats.successCount)
            assertEquals(0, stats.failureCount)
            assertEquals(listOf(DashboardMetricId.SPEED), processed)
            coVerify(exactly = 1) {
                metricsStore.publish(7L, DashboardMetricId.SPEED, "42", "kmh", 0f, 200f)
            }
            verify(exactly = 1) { logManager.command("010D (Speed)") }
            verify(exactly = 1) { logManager.response("010D: 42 (raw=41 0D 2A)") }
        }

    @Test
    fun `readDueMetrics records failure when session command fails`() =
        runTest {
            val processed = mutableListOf<DashboardMetricId>()

            coEvery {
                sessionDataSource.withConnectedSession<Triple<ObdResponse, String, String>>(any())
            } returns Result.failure(Exception("Not connected"))

            val stats =
                reader.readDueMetrics(7L, listOf(DashboardMetricId.SPEED)) {
                    processed += it
                }

            assertEquals(1, stats.commandCount)
            assertEquals(0, stats.successCount)
            assertEquals(1, stats.failureCount)
            assertEquals(listOf(DashboardMetricId.SPEED), processed)
            coVerify(exactly = 0) {
                metricsStore.publish(any(), any(), any(), any(), any(), any())
            }
            verify(exactly = 1) { logManager.error("Read error for 010D: Not connected") }
        }

    private fun response(
        command: com.github.eltonvs.obd.command.ObdCommand,
        value: String,
        rawValue: String,
        unit: String = "",
    ): ObdResponse = ObdResponse(command, ObdRawResponse(rawValue, 0L), value, unit)
}
