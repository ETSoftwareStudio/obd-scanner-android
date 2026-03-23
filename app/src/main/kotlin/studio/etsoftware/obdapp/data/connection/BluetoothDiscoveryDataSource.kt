package studio.etsoftware.obdapp.data.connection

import kotlinx.coroutines.flow.StateFlow
import studio.etsoftware.obdapp.domain.model.DeviceInfo
import studio.etsoftware.obdapp.domain.model.DiscoveryState
import studio.etsoftware.obdapp.domain.model.PairingState

interface BluetoothDiscoveryDataSource {
    val discoveryState: StateFlow<DiscoveryState>
    val pairingState: StateFlow<PairingState>

    fun isBluetoothEnabled(): Boolean

    fun isLocationServicesEnabledForDiscovery(): Boolean

    fun startDiscovery(): Result<Unit>

    fun stopDiscovery()

    fun pairDevice(device: DeviceInfo): Result<Unit>

    fun clearPairingState()
}
