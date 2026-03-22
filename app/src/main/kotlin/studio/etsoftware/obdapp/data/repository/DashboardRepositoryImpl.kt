package studio.etsoftware.obdapp.data.repository

import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.StateFlow
import studio.etsoftware.obdapp.data.polling.DashboardMetricsStore
import studio.etsoftware.obdapp.data.polling.DashboardPollingCoordinator
import studio.etsoftware.obdapp.domain.model.DashboardMetricsSnapshot
import studio.etsoftware.obdapp.domain.repository.DashboardRepository

@Singleton
class DashboardRepositoryImpl
    @Inject
    constructor(
        private val metricsStore: DashboardMetricsStore,
        private val pollingCoordinator: DashboardPollingCoordinator,
    ) : DashboardRepository {
        override val dashboardMetrics: StateFlow<DashboardMetricsSnapshot> = metricsStore.dashboardMetrics

        override suspend fun startPolling(intervalMs: Long) {
            pollingCoordinator.startPolling(intervalMs)
        }

        override suspend fun stopPolling() {
            pollingCoordinator.stopPolling()
        }
    }
