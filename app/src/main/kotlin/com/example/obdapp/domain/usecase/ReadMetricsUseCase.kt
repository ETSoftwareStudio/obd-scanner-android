package com.example.obdapp.domain.usecase

import com.example.obdapp.domain.model.VehicleMetric
import com.example.obdapp.domain.repository.ObdRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class ReadMetricsUseCase @Inject constructor(
    private val repository: ObdRepository
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
