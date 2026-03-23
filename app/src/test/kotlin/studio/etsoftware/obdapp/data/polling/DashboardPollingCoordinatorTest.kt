package studio.etsoftware.obdapp.data.polling

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import studio.etsoftware.obdapp.data.logging.LogManager
import studio.etsoftware.obdapp.data.session.ObdSessionDataSource
import studio.etsoftware.obdapp.data.telemetry.TelemetryRecorder

class DashboardPollingCoordinatorTest {
    private val logManager = mockk<LogManager>(relaxed = true)
    private val telemetryRecorder = mockk<TelemetryRecorder>(relaxed = true)
    private val dashboardMetricReader = mockk<DashboardMetricReader>(relaxed = true)
    private val sessionDataSource = mockk<ObdSessionDataSource>(relaxed = true)
    private val coordinator =
        DashboardPollingCoordinator(
            logManager = logManager,
            telemetryRecorder = telemetryRecorder,
            dashboardMetricReader = dashboardMetricReader,
            sessionDataSource = sessionDataSource,
        )

    @Test
    fun `runWithPollingPaused returns block result`() =
        runTest {
            val result =
                coordinator.runWithPollingPaused(
                    reason = "diagnostics read",
                    resumeLabel = "diagnostics read",
                ) {
                    Result.success("ok")
                }

            assertTrue(result.isSuccess)
            assertEquals("ok", result.getOrNull())
        }

    @Test
    fun `runWithPollingPaused converts thrown exception into failure and invokes callback`() =
        runTest {
            var capturedMessage: String? = null

            val result: Result<Unit> =
                coordinator.runWithPollingPaused(
                    reason = "diagnostics read",
                    resumeLabel = "diagnostics read",
                    onFailure = { capturedMessage = it.message },
                ) {
                    throw IllegalStateException("boom")
                }

            assertTrue(result.isFailure)
            assertEquals("boom", result.exceptionOrNull()?.message)
            assertEquals("boom", capturedMessage)
        }

    @Test
    fun `runWithPollingPaused does not consult session when polling is inactive`() =
        runTest {
            val result =
                coordinator.runWithPollingPaused(
                    reason = "diagnostics read",
                    resumeLabel = "diagnostics read",
                ) {
                    Result.success("ok")
                }

            assertTrue(result.isSuccess)
            coVerify(exactly = 0) { sessionDataSource.withConnectedSession<Unit>(any()) }
        }
}
