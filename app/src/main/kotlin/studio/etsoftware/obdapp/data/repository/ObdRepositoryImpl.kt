package studio.etsoftware.obdapp.data.repository

import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import studio.etsoftware.obdapp.data.connection.BluetoothDiscoveryManager
import studio.etsoftware.obdapp.domain.model.ConnectionState
import studio.etsoftware.obdapp.domain.model.DashboardMetricsSnapshot
import studio.etsoftware.obdapp.domain.model.DeviceInfo
import studio.etsoftware.obdapp.domain.model.DiagnosticInfo
import studio.etsoftware.obdapp.domain.model.DiscoveryState
import studio.etsoftware.obdapp.domain.model.PairingState
import studio.etsoftware.obdapp.domain.model.VehicleMetric
import studio.etsoftware.obdapp.domain.repository.ConnectionRepository
import studio.etsoftware.obdapp.domain.repository.DashboardRepository
import studio.etsoftware.obdapp.domain.repository.DiagnosticsRepository
import studio.etsoftware.obdapp.domain.repository.DiscoveryRepository
import studio.etsoftware.obdapp.util.LogManager

@Singleton
class ObdRepositoryImpl
    @Inject
    constructor(
        private val discoveryManager: BluetoothDiscoveryManager,
        private val logManager: LogManager,
        private val metricsStore: DashboardMetricsStore,
        private val sessionManager: ObdSessionManager,
        private val diagnosticsService: DiagnosticsService,
        private val pollingCoordinator: DashboardPollingCoordinator,
    ) : ConnectionRepository,
        DiscoveryRepository,
        DashboardRepository,
        DiagnosticsRepository {
        override val connectionState: StateFlow<ConnectionState> = sessionManager.connectionState
        override val discoveryState: StateFlow<DiscoveryState> = discoveryManager.discoveryState
        override val pairingState: StateFlow<PairingState> = discoveryManager.pairingState
        val vehicleMetrics: Flow<VehicleMetric> = metricsStore.vehicleMetrics
        override val dashboardMetrics: StateFlow<DashboardMetricsSnapshot> = metricsStore.dashboardMetrics

        override fun isBluetoothEnabled(): Boolean = discoveryManager.isBluetoothEnabled()

        override fun isLocationServicesEnabledForDiscovery(): Boolean =
            discoveryManager.isLocationServicesEnabledForDiscovery()

        override fun startDiscovery(): Result<Unit> = discoveryManager.startDiscovery()

        override fun stopDiscovery() = discoveryManager.stopDiscovery()

        override fun pairDevice(device: DeviceInfo): Result<Unit> = discoveryManager.pairDevice(device)

        override fun clearPairingState() = discoveryManager.clearPairingState()

        override suspend fun getPairedDevices(): List<DeviceInfo> = sessionManager.getPairedDevices()

        override suspend fun connect(device: DeviceInfo): Result<Unit> {
            stopDiscovery()
            return sessionManager.connect(device)
        }

        override suspend fun disconnect() {
            logManager.info("Disconnecting...")
            pollingCoordinator.stopPolling()
            sessionManager.disconnect()
            logManager.info("Disconnected")
        }

        override suspend fun readDiagnosticInfo(): Result<DiagnosticInfo> =
            withContext(Dispatchers.IO) {
                pollingCoordinator.runWithPollingPaused(
                    reason = "diagnostics read",
                    resumeLabel = "diagnostics read",
                ) { connection ->
                    Result.success(diagnosticsService.readDiagnosticInfo(connection))
                }
            }

        override suspend fun clearTroubleCodes(): Result<Unit> =
            withContext(Dispatchers.IO) {
                pollingCoordinator.runWithPollingPaused(
                    reason = "trouble code clear",
                    resumeLabel = "trouble code clear",
                    onFailure = { error -> logManager.error("Failed to clear trouble codes: ${error.message}") },
                ) { connection ->
                    diagnosticsService.clearTroubleCodes(connection)
                    Result.success(Unit)
                }
            }

        override suspend fun startPolling(intervalMs: Long) {
            pollingCoordinator.startPolling(intervalMs)
        }

        override suspend fun stopPolling() {
            pollingCoordinator.stopPolling()
        }
    }
