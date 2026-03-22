package studio.etsoftware.obdapp.data.repository

import app.cash.turbine.test
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import studio.etsoftware.obdapp.data.telemetry.TelemetryRecorder

class DashboardMetricsStoreTest {
    private val telemetryRecorder = mockk<TelemetryRecorder>(relaxed = true)
    private val store = DashboardMetricsStore(telemetryRecorder)

    @Test
    fun `publishing speed updates snapshot and metric flow`() =
        runTest {
            every { telemetryRecorder.currentSessionId() } returns "session"

            store.vehicleMetrics.test {
                store.publish(
                    cycleId = 7L,
                    metricId = DashboardMetricId.SPEED,
                    value = "88",
                    unit = "km/h",
                    minValue = 0f,
                    maxValue = 200f,
                )

                assertEquals("88", store.dashboardMetrics.value.speed)
                assertEquals("", store.dashboardMetrics.value.rpm)
                assertEquals("Speed", awaitItem().name)
                cancelAndIgnoreRemainingEvents()
            }

            coVerify(exactly = 1) { telemetryRecorder.recordMetricEmission(any()) }
        }

    @Test
    fun `publishing fuel only updates the fuel field`() =
        runTest {
            every { telemetryRecorder.currentSessionId() } returns "session"

            store.publish(1L, DashboardMetricId.SPEED, "55", "km/h", 0f, 200f)
            store.publish(2L, DashboardMetricId.FUEL, "40", "%", 0f, 100f)

            assertEquals("55", store.dashboardMetrics.value.speed)
            assertEquals("40", store.dashboardMetrics.value.fuel)
            assertEquals("", store.dashboardMetrics.value.coolantTemp)
        }
}
