package com.eltonvs.obdapp.domain.usecase

import com.eltonvs.obdapp.domain.model.VehicleMetric
import com.eltonvs.obdapp.domain.repository.ObdRepository
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow

class ReadMetricsUseCase
    @Inject
    constructor(
        private val repository: ObdRepository,
    ) {
        operator fun invoke(): Flow<VehicleMetric> {
            return repository.vehicleMetrics
        }

        suspend fun startPolling(intervalMs: Long) {
            repository.startPolling(intervalMs)
        }

        suspend fun stopPolling() {
            repository.stopPolling()
        }
    }
