package studio.etsoftware.obdapp.domain.repository

import studio.etsoftware.obdapp.domain.model.ConnectionState
import studio.etsoftware.obdapp.domain.model.DashboardMetricsSnapshot
import studio.etsoftware.obdapp.domain.model.DeviceInfo
import studio.etsoftware.obdapp.domain.model.DiagnosticInfo
import studio.etsoftware.obdapp.domain.model.DiscoveryState
import studio.etsoftware.obdapp.domain.model.PairingState
import studio.etsoftware.obdapp.domain.model.VehicleMetric
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

interface ObdRepository {
    val connectionState: StateFlow<ConnectionState>
    val discoveryState: StateFlow<DiscoveryState>
    val pairingState: StateFlow<PairingState>
    val vehicleMetrics: Flow<VehicleMetric>
    val dashboardMetrics: StateFlow<DashboardMetricsSnapshot>

    suspend fun getPairedDevices(): List<DeviceInfo>

    fun isBluetoothEnabled(): Boolean

    fun isLocationServicesEnabledForDiscovery(): Boolean

    fun startDiscovery(): Result<Unit>

    fun stopDiscovery()

    fun pairDevice(device: DeviceInfo): Result<Unit>

    fun clearPairingState()

    suspend fun connect(device: DeviceInfo): Result<Unit>

    suspend fun disconnect()

    suspend fun readDiagnosticInfo(): Result<DiagnosticInfo>

    suspend fun clearTroubleCodes(): Result<Unit>

    suspend fun startPolling(intervalMs: Long)

    suspend fun stopPolling()
}
