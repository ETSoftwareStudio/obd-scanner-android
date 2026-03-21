package com.eltonvs.obdapp.domain.usecase

import com.eltonvs.obdapp.domain.model.DashboardMetricsSnapshot
import com.eltonvs.obdapp.domain.repository.ObdRepository
import javax.inject.Inject
import kotlinx.coroutines.flow.StateFlow

class ReadMetricsUseCase
    @Inject
    constructor(
        private val repository: ObdRepository,
    ) {
        operator fun invoke(): StateFlow<DashboardMetricsSnapshot> {
            return repository.dashboardMetrics
        }

        suspend fun startPolling(intervalMs: Long) {
            repository.startPolling(intervalMs)
        }

        suspend fun stopPolling() {
            repository.stopPolling()
        }
    }
