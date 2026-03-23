package studio.etsoftware.obdapp.data.diagnostics

import com.github.eltonvs.obd.command.ObdRawResponse
import com.github.eltonvs.obd.command.ObdResponse
import com.github.eltonvs.obd.command.control.ResetTroubleCodesCommand
import com.github.eltonvs.obd.command.control.TroubleCodesCommand
import com.github.eltonvs.obd.command.control.VINCommand
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import studio.etsoftware.obdapp.data.logging.LogManager
import studio.etsoftware.obdapp.data.session.ObdCommandSession
import studio.etsoftware.obdapp.data.session.ObdSessionDataSource
import studio.etsoftware.obdapp.domain.model.TelemetryContext

class DiagnosticsServiceTest {
    private val sessionDataSource = mockk<ObdSessionDataSource>()
    private val session = mockk<ObdCommandSession>()
    private val logManager = mockk<LogManager>(relaxed = true)
    private val service = DiagnosticsService(DtcParser(), logManager, sessionDataSource)

    @Test
    fun `readDiagnosticInfo maps VIN and parsed trouble codes`() =
        runTest {
            val vinResponse = response(VINCommand(), value = "1HGCM82633A004352", rawValue = "490201314847...")
            val troubleCodesResponse = response(TroubleCodesCommand(), value = "P0301 P0420 P0000", rawValue = "43 01 02")

            coEvery {
                sessionDataSource.withConnectedSession<studio.etsoftware.obdapp.domain.model.DiagnosticInfo>(any())
            } coAnswers {
                firstArg<suspend (ObdCommandSession) -> Result<studio.etsoftware.obdapp.domain.model.DiagnosticInfo>>().invoke(session)
            }
            coEvery {
                session.run(TelemetryContext.DIAGNOSTICS, null, match { it is VINCommand }, any())
            } returns vinResponse
            coEvery {
                session.run(TelemetryContext.DIAGNOSTICS, null, match { it is TroubleCodesCommand }, any())
            } returns troubleCodesResponse

            val result = service.readDiagnosticInfo()

            assertTrue(result.isSuccess)
            val diagnosticInfo = result.getOrThrow()
            assertEquals("1HGCM82633A004352", diagnosticInfo.vin)
            assertEquals(listOf("P0301", "P0420"), diagnosticInfo.troubleCodes.map { it.code })
            assertTrue(diagnosticInfo.milStatus)
            assertEquals(2, diagnosticInfo.dtcCount)
        }

    @Test
    fun `clearTroubleCodes executes reset command and logs success`() =
        runTest {
            val clearResponse = response(ResetTroubleCodesCommand(), value = "OK", rawValue = "44")

            coEvery {
                sessionDataSource.withConnectedSession<Unit>(any())
            } coAnswers {
                firstArg<suspend (ObdCommandSession) -> Result<Unit>>().invoke(session)
            }
            coEvery {
                session.run(TelemetryContext.CLEAR_DTC, null, match { it is ResetTroubleCodesCommand }, any())
            } returns clearResponse

            val result = service.clearTroubleCodes()

            assertTrue(result.isSuccess)
            coVerify(exactly = 1) {
                session.run(TelemetryContext.CLEAR_DTC, null, match { it is ResetTroubleCodesCommand }, any())
            }
            verify(exactly = 1) { logManager.command("04 (Clear trouble codes)") }
            verify(exactly = 1) { logManager.success("Trouble codes clear command sent") }
        }

    @Test
    fun `readDiagnosticInfo returns failure when session is unavailable`() =
        runTest {
            coEvery {
                sessionDataSource.withConnectedSession<studio.etsoftware.obdapp.domain.model.DiagnosticInfo>(any())
            } returns Result.failure(Exception("Not connected"))

            val result = service.readDiagnosticInfo()

            assertTrue(result.isFailure)
            assertEquals("Not connected", result.exceptionOrNull()?.message)
        }

    private fun response(
        command: com.github.eltonvs.obd.command.ObdCommand,
        value: String,
        rawValue: String,
        unit: String = "",
    ): ObdResponse = ObdResponse(command, ObdRawResponse(rawValue, 0L), value, unit)
}
