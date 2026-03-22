package studio.etsoftware.obdapp.domain.repository

import kotlinx.coroutines.flow.StateFlow
import studio.etsoftware.obdapp.domain.model.DashboardMetricsSnapshot

interface DashboardRepository {
    val dashboardMetrics: StateFlow<DashboardMetricsSnapshot>

    suspend fun startPolling(intervalMs: Long)

    suspend fun stopPolling()
}
