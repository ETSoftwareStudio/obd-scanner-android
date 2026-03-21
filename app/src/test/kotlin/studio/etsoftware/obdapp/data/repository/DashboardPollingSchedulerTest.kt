package studio.etsoftware.obdapp.data.repository

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DashboardPollingSchedulerTest {
    @Test
    fun `all metrics are due at scheduler start`() {
        val scheduler = DashboardPollingScheduler(baseIntervalMs = 500L, startAtMs = 1_000L)

        assertEquals(DashboardPollingScheduler.ORDERED_METRICS, scheduler.dueMetrics(1_000L))
    }

    @Test
    fun `fast metric becomes due on base interval`() {
        val scheduler = DashboardPollingScheduler(baseIntervalMs = 500L, startAtMs = 0L)

        scheduler.markExecuted(DashboardMetricId.SPEED, executedAtMs = 500L)

        assertFalse(scheduler.dueMetrics(999L).contains(DashboardMetricId.SPEED))
        assertTrue(scheduler.dueMetrics(1_000L).contains(DashboardMetricId.SPEED))
    }

    @Test
    fun `medium and slow metrics use tiered intervals`() {
        val scheduler = DashboardPollingScheduler(baseIntervalMs = 500L, startAtMs = 0L)

        scheduler.markExecuted(DashboardMetricId.MAF, executedAtMs = 500L)
        scheduler.markExecuted(DashboardMetricId.COOLANT, executedAtMs = 500L)

        assertFalse(scheduler.dueMetrics(2_999L).contains(DashboardMetricId.MAF))
        assertTrue(scheduler.dueMetrics(3_000L).contains(DashboardMetricId.MAF))

        assertFalse(scheduler.dueMetrics(5_999L).contains(DashboardMetricId.COOLANT))
        assertTrue(scheduler.dueMetrics(6_000L).contains(DashboardMetricId.COOLANT))
    }

    @Test
    fun `delay until next work tracks earliest due metric`() {
        val scheduler = DashboardPollingScheduler(baseIntervalMs = 500L, startAtMs = 0L)

        DashboardPollingScheduler.ORDERED_METRICS.forEach { metricId ->
            scheduler.markExecuted(metricId, executedAtMs = 500L)
        }

        assertEquals(500L, scheduler.delayUntilNextWork(500L))
        assertEquals(0L, scheduler.delayUntilNextWork(1_000L))
    }
}
