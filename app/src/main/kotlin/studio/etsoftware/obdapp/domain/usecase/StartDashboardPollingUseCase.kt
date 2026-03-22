package studio.etsoftware.obdapp.domain.usecase

import javax.inject.Inject
import studio.etsoftware.obdapp.domain.repository.DashboardRepository

class StartDashboardPollingUseCase
    @Inject
    constructor(
        private val dashboardRepository: DashboardRepository,
    ) {
        suspend operator fun invoke(intervalMs: Long) {
            dashboardRepository.startPolling(intervalMs)
        }
    }
