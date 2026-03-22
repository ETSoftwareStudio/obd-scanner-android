package studio.etsoftware.obdapp.data.repository

import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.StateFlow
import studio.etsoftware.obdapp.data.connection.BluetoothDiscoveryManager
import studio.etsoftware.obdapp.data.polling.DashboardPollingCoordinator
import studio.etsoftware.obdapp.data.session.ObdSessionManager
import studio.etsoftware.obdapp.domain.model.ConnectionState
import studio.etsoftware.obdapp.domain.model.DeviceInfo
import studio.etsoftware.obdapp.domain.repository.ConnectionRepository
import studio.etsoftware.obdapp.data.logging.LogManager

@Singleton
class ConnectionRepositoryImpl
    @Inject
    constructor(
        private val discoveryManager: BluetoothDiscoveryManager,
        private val sessionManager: ObdSessionManager,
        private val pollingCoordinator: DashboardPollingCoordinator,
        private val logManager: LogManager,
    ) : ConnectionRepository {
        override val connectionState: StateFlow<ConnectionState> = sessionManager.connectionState

        override suspend fun getPairedDevices(): List<DeviceInfo> = sessionManager.getPairedDevices()

        override suspend fun connect(device: DeviceInfo): Result<Unit> {
            discoveryManager.stopDiscovery()
            return sessionManager.connect(device)
        }

        override suspend fun disconnect() {
            logManager.info("Disconnecting...")
            pollingCoordinator.stopPolling()
            sessionManager.disconnect()
            logManager.info("Disconnected")
        }
    }
