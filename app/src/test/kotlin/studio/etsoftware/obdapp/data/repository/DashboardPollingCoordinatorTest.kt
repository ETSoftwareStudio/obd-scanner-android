package studio.etsoftware.obdapp.data.repository

import com.github.eltonvs.obd.connection.ObdDeviceConnection
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import studio.etsoftware.obdapp.data.polling.DashboardMetricsStore
import studio.etsoftware.obdapp.data.polling.DashboardPollingCoordinator
import studio.etsoftware.obdapp.data.session.ObdCommandExecutor
import studio.etsoftware.obdapp.data.session.ObdSessionManager
import studio.etsoftware.obdapp.data.telemetry.TelemetryRecorder
import studio.etsoftware.obdapp.domain.model.ConnectionState
import studio.etsoftware.obdapp.util.LogManager

class DashboardPollingCoordinatorTest {
    private val logManager = mockk<LogManager>(relaxed = true)
    private val telemetryRecorder = mockk<TelemetryRecorder>(relaxed = true)
    private val metricsStore = mockk<DashboardMetricsStore>(relaxed = true)
    private val commandExecutor = mockk<ObdCommandExecutor>(relaxed = true)
    private val sessionManager = mockk<ObdSessionManager>()
    private val coordinator =
        DashboardPollingCoordinator(
            logManager = logManager,
            telemetryRecorder = telemetryRecorder,
            metricsStore = metricsStore,
            commandExecutor = commandExecutor,
            sessionManager = sessionManager,
        )

    @Test
    fun `runWithPollingPaused returns failure when session is missing`() =
        runTest {
            every { sessionManager.connectionState } returns MutableStateFlow(ConnectionState.Connected)
            every { sessionManager.currentConnection() } returns null
            every { sessionManager.isTransportConnected() } returns false
            coEvery {
                sessionManager.withConnectionAccess<Result<String>>(any())
            } coAnswers {
                firstArg<suspend () -> Result<String>>().invoke()
            }

            val result =
                coordinator.runWithPollingPaused(
                    reason = "diagnostics read",
                    resumeLabel = "diagnostics read",
                ) { Result.success("unused") }

            assertTrue(result.isFailure)
            assertEquals("Not connected", result.exceptionOrNull()?.message)
        }

    @Test
    fun `runWithPollingPaused executes the provided block when connected`() =
        runTest {
            val connection = mockk<ObdDeviceConnection>()

            every { sessionManager.connectionState } returns MutableStateFlow(ConnectionState.Connected)
            every { sessionManager.currentConnection() } returns connection
            every { sessionManager.isTransportConnected() } returns true
            coEvery {
                sessionManager.withConnectionAccess<Result<String>>(any())
            } coAnswers {
                firstArg<suspend () -> Result<String>>().invoke()
            }

            val result =
                coordinator.runWithPollingPaused(
                    reason = "diagnostics read",
                    resumeLabel = "diagnostics read",
                ) { activeConnection ->
                    assertEquals(connection, activeConnection)
                    Result.success("ok")
                }

            assertTrue(result.isSuccess)
            assertEquals("ok", result.getOrNull())
        }
}
