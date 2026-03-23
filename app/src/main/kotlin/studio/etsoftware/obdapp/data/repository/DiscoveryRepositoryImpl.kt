package studio.etsoftware.obdapp.data.repository

import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.StateFlow
import studio.etsoftware.obdapp.data.connection.BluetoothDiscoveryDataSource
import studio.etsoftware.obdapp.domain.model.DeviceInfo
import studio.etsoftware.obdapp.domain.model.DiscoveryState
import studio.etsoftware.obdapp.domain.model.PairingState
import studio.etsoftware.obdapp.domain.repository.DiscoveryRepository

@Singleton
class DiscoveryRepositoryImpl
    @Inject
    constructor(
        private val discoveryDataSource: BluetoothDiscoveryDataSource,
    ) : DiscoveryRepository {
        override val discoveryState: StateFlow<DiscoveryState> = discoveryDataSource.discoveryState
        override val pairingState: StateFlow<PairingState> = discoveryDataSource.pairingState

        override fun isBluetoothEnabled(): Boolean = discoveryDataSource.isBluetoothEnabled()

        override fun isLocationServicesEnabledForDiscovery(): Boolean = discoveryDataSource.isLocationServicesEnabledForDiscovery()

        override fun startDiscovery(): Result<Unit> = discoveryDataSource.startDiscovery()

        override fun stopDiscovery() = discoveryDataSource.stopDiscovery()

        override fun pairDevice(device: DeviceInfo): Result<Unit> = discoveryDataSource.pairDevice(device)

        override fun clearPairingState() = discoveryDataSource.clearPairingState()
    }
