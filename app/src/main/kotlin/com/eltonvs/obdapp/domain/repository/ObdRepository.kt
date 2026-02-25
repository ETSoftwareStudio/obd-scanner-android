package com.eltonvs.obdapp.domain.repository

import com.eltonvs.obdapp.domain.model.ConnectionState
import com.eltonvs.obdapp.domain.model.DeviceInfo
import com.eltonvs.obdapp.domain.model.DiagnosticInfo
import com.eltonvs.obdapp.domain.model.VehicleMetric
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

interface ObdRepository {
    val connectionState: StateFlow<ConnectionState>
    val vehicleMetrics: Flow<VehicleMetric>

    suspend fun getPairedDevices(): List<DeviceInfo>

    suspend fun connect(device: DeviceInfo): Result<Unit>

    suspend fun disconnect()

    suspend fun readDiagnosticInfo(): Result<DiagnosticInfo>

    suspend fun startPolling(intervalMs: Long)

    suspend fun stopPolling()
}
