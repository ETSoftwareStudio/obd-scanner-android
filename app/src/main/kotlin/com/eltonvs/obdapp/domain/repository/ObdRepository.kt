package com.eltonvs.obdapp.domain.repository

import com.eltonvs.obdapp.domain.model.ConnectionState
import com.eltonvs.obdapp.domain.model.DeviceInfo
import com.eltonvs.obdapp.domain.model.DiagnosticInfo
import com.eltonvs.obdapp.domain.model.DiscoveryState
import com.eltonvs.obdapp.domain.model.PairingState
import com.eltonvs.obdapp.domain.model.VehicleMetric
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

interface ObdRepository {
    val connectionState: StateFlow<ConnectionState>
    val discoveryState: StateFlow<DiscoveryState>
    val pairingState: StateFlow<PairingState>
    val vehicleMetrics: Flow<VehicleMetric>

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
