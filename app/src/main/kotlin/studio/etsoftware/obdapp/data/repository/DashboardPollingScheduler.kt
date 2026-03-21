package studio.etsoftware.obdapp.data.repository

internal enum class DashboardMetricId {
    SPEED,
    RPM,
    THROTTLE,
    MAF,
    COOLANT,
    INTAKE,
    FUEL,
}

internal enum class MetricPollingTier {
    FAST,
    MEDIUM,
    SLOW,
}

internal class DashboardPollingScheduler(
    private val baseIntervalMs: Long,
    startAtMs: Long,
) {
    private val nextDueAtMs =
        ORDERED_METRICS
            .associateWith { startAtMs }
            .toMutableMap()

    fun dueMetrics(nowMs: Long): List<DashboardMetricId> {
        return ORDERED_METRICS.filter { metricId ->
            nextDueAtMs.getValue(metricId) <= nowMs
        }
    }

    fun markExecuted(
        metricId: DashboardMetricId,
        executedAtMs: Long,
    ) {
        val currentDueAt = nextDueAtMs.getValue(metricId)
        val intervalMs = intervalFor(metricId)
        var nextDueAt = currentDueAt + intervalMs

        if (nextDueAt <= executedAtMs) {
            val missedIntervals = ((executedAtMs - currentDueAt) / intervalMs) + 1
            nextDueAt = currentDueAt + (missedIntervals * intervalMs)
        }

        nextDueAtMs[metricId] = nextDueAt
    }

    fun delayUntilNextWork(nowMs: Long): Long {
        val nextDueAt = nextDueAtMs.values.minOrNull() ?: nowMs
        return (nextDueAt - nowMs).coerceAtLeast(0L)
    }

    private fun intervalFor(metricId: DashboardMetricId): Long {
        return when (TIER_BY_METRIC.getValue(metricId)) {
            MetricPollingTier.FAST -> baseIntervalMs.coerceAtLeast(MIN_FAST_INTERVAL_MS)
            MetricPollingTier.MEDIUM -> maxOf(baseIntervalMs * MEDIUM_INTERVAL_MULTIPLIER, MIN_MEDIUM_INTERVAL_MS)
            MetricPollingTier.SLOW -> maxOf(baseIntervalMs * SLOW_INTERVAL_MULTIPLIER, MIN_SLOW_INTERVAL_MS)
        }
    }

    companion object {
        val ORDERED_METRICS: List<DashboardMetricId> =
            listOf(
                DashboardMetricId.SPEED,
                DashboardMetricId.RPM,
                DashboardMetricId.THROTTLE,
                DashboardMetricId.MAF,
                DashboardMetricId.COOLANT,
                DashboardMetricId.INTAKE,
                DashboardMetricId.FUEL,
            )

        private val TIER_BY_METRIC: Map<DashboardMetricId, MetricPollingTier> =
            mapOf(
                DashboardMetricId.SPEED to MetricPollingTier.FAST,
                DashboardMetricId.RPM to MetricPollingTier.FAST,
                DashboardMetricId.THROTTLE to MetricPollingTier.FAST,
                DashboardMetricId.MAF to MetricPollingTier.MEDIUM,
                DashboardMetricId.COOLANT to MetricPollingTier.SLOW,
                DashboardMetricId.INTAKE to MetricPollingTier.SLOW,
                DashboardMetricId.FUEL to MetricPollingTier.SLOW,
            )

        private const val MIN_FAST_INTERVAL_MS = 500L
        private const val MIN_MEDIUM_INTERVAL_MS = 3000L
        private const val MIN_SLOW_INTERVAL_MS = 6000L
        private const val MEDIUM_INTERVAL_MULTIPLIER = 3L
        private const val SLOW_INTERVAL_MULTIPLIER = 6L
    }
}
