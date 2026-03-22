package studio.etsoftware.obdapp.data.repository

import com.github.eltonvs.obd.command.ObdRawResponse
import com.github.eltonvs.obd.command.ObdResponse
import com.github.eltonvs.obd.command.control.ResetTroubleCodesCommand
import com.github.eltonvs.obd.command.control.TroubleCodesCommand
import com.github.eltonvs.obd.command.control.VINCommand
import com.github.eltonvs.obd.connection.ObdDeviceConnection
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import studio.etsoftware.obdapp.data.diagnostics.DiagnosticsService
import studio.etsoftware.obdapp.data.diagnostics.DtcParser
import studio.etsoftware.obdapp.data.session.ObdCommandExecutor
import studio.etsoftware.obdapp.domain.model.TelemetryContext
import studio.etsoftware.obdapp.util.LogManager

class DiagnosticsServiceTest {
    private val commandExecutor = mockk<ObdCommandExecutor>()
    private val connection = mockk<ObdDeviceConnection>()
    private val logManager = mockk<LogManager>(relaxed = true)
    private val service = DiagnosticsService(commandExecutor, DtcParser(), logManager)

    @Test
    fun `readDiagnosticInfo maps VIN and parsed trouble codes`() =
        runTest {
            val vinResponse = response(VINCommand(), value = "1HGCM82633A004352", rawValue = "490201314847...")
            val troubleCodesResponse = response(TroubleCodesCommand(), value = "P0301 P0420 P0000", rawValue = "43 01 02")

            coEvery {
                commandExecutor.execute<ObdResponse>(TelemetryContext.DIAGNOSTICS, null, "VIN", "VINCommand", any(), any())
            } returns vinResponse
            coEvery {
                commandExecutor.execute<ObdResponse>(TelemetryContext.DIAGNOSTICS, null, "03", "TroubleCodesCommand", any(), any())
            } returns troubleCodesResponse

            val result = service.readDiagnosticInfo(connection)

            assertEquals("1HGCM82633A004352", result.vin)
            assertEquals(listOf("P0301", "P0420"), result.troubleCodes.map { it.code })
            assertTrue(result.milStatus)
            assertEquals(2, result.dtcCount)
        }

    @Test
    fun `clearTroubleCodes executes reset command and logs success`() =
        runTest {
            val clearResponse = response(ResetTroubleCodesCommand(), value = "OK", rawValue = "44")
            coEvery {
                commandExecutor.execute<ObdResponse>(TelemetryContext.CLEAR_DTC, null, "04", "ResetTroubleCodesCommand", any(), any())
            } returns clearResponse

            service.clearTroubleCodes(connection)

            coVerify(exactly = 1) {
                commandExecutor.execute<ObdResponse>(TelemetryContext.CLEAR_DTC, null, "04", "ResetTroubleCodesCommand", any(), any())
            }
            io.mockk.verify(exactly = 1) { logManager.command("04 (Clear trouble codes)") }
            io.mockk.verify(exactly = 1) { logManager.success("Trouble codes clear command sent") }
        }

    private fun response(
        command: com.github.eltonvs.obd.command.ObdCommand,
        value: String,
        rawValue: String,
        unit: String = "",
    ): ObdResponse {
        return ObdResponse(command, ObdRawResponse(rawValue, 0L), value, unit)
    }
}
