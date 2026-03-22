package studio.etsoftware.obdapp.domain.usecase

import studio.etsoftware.obdapp.domain.model.DashboardMetricsSnapshot
import studio.etsoftware.obdapp.domain.repository.DashboardRepository
import javax.inject.Inject
import kotlinx.coroutines.flow.StateFlow

class ReadMetricsUseCase
    @Inject
    constructor(
        private val dashboardRepository: DashboardRepository,
    ) {
        operator fun invoke(): StateFlow<DashboardMetricsSnapshot> {
            return dashboardRepository.dashboardMetrics
        }

        suspend fun startPolling(intervalMs: Long) {
            dashboardRepository.startPolling(intervalMs)
        }

        suspend fun stopPolling() {
            dashboardRepository.stopPolling()
        }
    }
