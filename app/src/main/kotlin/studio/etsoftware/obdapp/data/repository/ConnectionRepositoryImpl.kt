package studio.etsoftware.obdapp.data.repository

import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.StateFlow
import studio.etsoftware.obdapp.data.connection.BluetoothDiscoveryDataSource
import studio.etsoftware.obdapp.data.polling.DashboardPollingCoordinator
import studio.etsoftware.obdapp.data.session.ObdSessionDataSource
import studio.etsoftware.obdapp.domain.model.ConnectionState
import studio.etsoftware.obdapp.domain.model.DeviceInfo
import studio.etsoftware.obdapp.domain.repository.ConnectionRepository
import studio.etsoftware.obdapp.data.logging.LogManager

@Singleton
class ConnectionRepositoryImpl
    @Inject
    constructor(
        private val discoveryDataSource: BluetoothDiscoveryDataSource,
        private val sessionDataSource: ObdSessionDataSource,
        private val pollingCoordinator: DashboardPollingCoordinator,
        private val logManager: LogManager,
    ) : ConnectionRepository {
        override val connectionState: StateFlow<ConnectionState> = sessionDataSource.connectionState

        override suspend fun getPairedDevices(): List<DeviceInfo> = sessionDataSource.getPairedDevices()

        override suspend fun connect(device: DeviceInfo): Result<Unit> {
            discoveryDataSource.stopDiscovery()
            return sessionDataSource.connect(device)
        }

        override suspend fun disconnect() {
            logManager.info("Disconnecting...")
            pollingCoordinator.stopPolling()
            sessionDataSource.disconnect()
            logManager.info("Disconnected")
        }
    }
