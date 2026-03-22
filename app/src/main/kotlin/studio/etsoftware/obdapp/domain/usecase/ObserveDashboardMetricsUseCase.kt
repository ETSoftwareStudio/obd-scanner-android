package studio.etsoftware.obdapp.domain.usecase

import javax.inject.Inject
import kotlinx.coroutines.flow.StateFlow
import studio.etsoftware.obdapp.domain.model.DashboardMetricsSnapshot
import studio.etsoftware.obdapp.domain.repository.DashboardRepository

class ObserveDashboardMetricsUseCase
    @Inject
    constructor(
        private val dashboardRepository: DashboardRepository,
    ) {
        operator fun invoke(): StateFlow<DashboardMetricsSnapshot> = dashboardRepository.dashboardMetrics
    }
