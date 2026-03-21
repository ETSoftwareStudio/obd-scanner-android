package studio.etsoftware.obdapp.ui.feature.dashboard

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import studio.etsoftware.obdapp.domain.model.ConnectionState
import studio.etsoftware.obdapp.domain.model.DeviceInfo
import studio.etsoftware.obdapp.domain.model.DeviceType
import studio.etsoftware.obdapp.domain.repository.ObdRepository
import studio.etsoftware.obdapp.domain.usecase.ClearTelemetryUseCase
import studio.etsoftware.obdapp.domain.usecase.ConnectDeviceUseCase
import studio.etsoftware.obdapp.domain.usecase.DisconnectUseCase
import studio.etsoftware.obdapp.domain.usecase.ObservePollingIntervalUseCase
import studio.etsoftware.obdapp.domain.usecase.ObserveTelemetryEventsUseCase
import studio.etsoftware.obdapp.domain.usecase.ReadMetricsUseCase
import studio.etsoftware.obdapp.util.LogEntry
import studio.etsoftware.obdapp.util.LogExportFormatter
import studio.etsoftware.obdapp.util.LogExporter
import studio.etsoftware.obdapp.util.LogManager
import studio.etsoftware.obdapp.util.PreferencesManager
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class DashboardUiState(
    val speed: String = "--",
    val rpm: String = "--",
    val throttle: String = "--",
    val coolantTemp: String = "--",
    val intakeTemp: String = "--",
    val maf: String = "--",
    val fuel: String = "--",
    val connectionState: ConnectionState = ConnectionState.Disconnected,
    val isPolling: Boolean = false,
    val autoConnectEnabled: Boolean = true,
)

@HiltViewModel
class DashboardViewModel
    @Inject
    constructor(
        private val readMetricsUseCase: ReadMetricsUseCase,
        private val disconnectUseCase: DisconnectUseCase,
        private val connectDeviceUseCase: ConnectDeviceUseCase,
        private val repository: ObdRepository,
        private val preferencesManager: PreferencesManager,
        private val logExportFormatter: LogExportFormatter,
        private val logExporter: LogExporter,
        private val logManager: LogManager,
        private val observePollingIntervalUseCase: ObservePollingIntervalUseCase,
        private val observeTelemetryEventsUseCase: ObserveTelemetryEventsUseCase,
        private val clearTelemetryUseCase: ClearTelemetryUseCase,
    ) : ViewModel() {
        private val _uiState = MutableStateFlow(DashboardUiState())
        val uiState: StateFlow<DashboardUiState> = _uiState.asStateFlow()

        val logs: StateFlow<List<LogEntry>> = logManager.logs
        private val telemetryEvents = observeTelemetryEventsUseCase()

        private val _events = MutableSharedFlow<DashboardEvent>(extraBufferCapacity = 1)
        val events: SharedFlow<DashboardEvent> = _events.asSharedFlow()

        private var hasCheckedAutoConnect = false
        private var latestPollingIntervalMs: Long? = null
        private var activePollingIntervalMs: Long? = null

        init {
            observeConnectionState()
            observeMetrics()
            observePollingInterval()
            checkAutoConnect()
        }

        private fun checkAutoConnect() {
            if (hasCheckedAutoConnect) return
            hasCheckedAutoConnect = true

            viewModelScope.launch {
                val autoConnect = preferencesManager.autoConnect.first()
                val wasConnected = preferencesManager.wasConnected.first()
                val lastDeviceAddress = preferencesManager.lastDeviceAddress.first()
                val lastDeviceName = preferencesManager.lastDeviceName.first()
                val currentState = repository.connectionState.value

                _uiState.update { it.copy(autoConnectEnabled = autoConnect) }

                if (currentState is ConnectionState.Connected) {
                    startPollingIfNeeded()
                    return@launch
                }

                if (autoConnect && wasConnected && lastDeviceAddress != null && lastDeviceName != null) {
                    val device = DeviceInfo(lastDeviceAddress, lastDeviceName, DeviceType.CLASSIC)
                    connectDeviceUseCase(device)
                }
            }
        }

        private fun observeConnectionState() {
            viewModelScope.launch {
                repository.connectionState.collect { state ->
                    _uiState.update {
                        it.copy(
                            connectionState = state,
                            isPolling = if (state is ConnectionState.Connected) it.isPolling else false,
                        )
                    }
                    if (state !is ConnectionState.Connected) {
                        activePollingIntervalMs = null
                    }
                    if (state is ConnectionState.Connected) {
                        startPollingIfNeeded()
                    }
                }
            }
        }

        private fun observeMetrics() {
            viewModelScope.launch {
                readMetricsUseCase().collect { snapshot ->
                    _uiState.update {
                        it.copy(
                            speed = snapshot.speed,
                            rpm = snapshot.rpm,
                            throttle = snapshot.throttle,
                            coolantTemp = snapshot.coolantTemp,
                            intakeTemp = snapshot.intakeTemp,
                            maf = snapshot.maf,
                            fuel = snapshot.fuel,
                        )
                    }
                }
            }
        }

        private fun observePollingInterval() {
            viewModelScope.launch {
                observePollingIntervalUseCase().collect { interval ->
                    latestPollingIntervalMs = interval

                    val shouldRestart =
                        repository.connectionState.value is ConnectionState.Connected &&
                            _uiState.value.isPolling &&
                            activePollingIntervalMs != null &&
                            activePollingIntervalMs != interval

                    if (shouldRestart) {
                        startPolling(interval)
                    }
                }
            }
        }

        private fun startPollingIfNeeded() {
            if (_uiState.value.isPolling) return
            viewModelScope.launch {
                val interval = latestPollingIntervalMs ?: observePollingIntervalUseCase().first()
                startPolling(interval)
            }
        }

        fun stopPolling() {
            viewModelScope.launch {
                stopPollingInternal()
            }
        }

        fun disconnect() {
            viewModelScope.launch {
                stopPollingInternal()
                disconnectUseCase()
            }
        }

        fun clearLogs() {
            logManager.clear()
            viewModelScope.launch {
                clearTelemetryUseCase()
            }
        }

        fun getSuggestedLogFileName(): String {
            return logExportFormatter.buildDefaultFileName()
        }

        fun exportLogs(uri: Uri?) {
            if (uri == null) {
                return
            }

            val logSnapshot = logs.value
            if (logSnapshot.isEmpty()) {
                emitEvent(DashboardEvent.ExportSkippedNoLogs)
                return
            }

            viewModelScope.launch {
                val exportText =
                    logExportFormatter.buildExportText(
                        logs = logSnapshot,
                        telemetryEvents = telemetryEvents.value,
                    )
                logExporter.export(uri, exportText).fold(
                    onSuccess = {
                        emitEvent(DashboardEvent.ExportSuccess)
                    },
                    onFailure = { error ->
                        emitEvent(DashboardEvent.ExportError(error.message))
                    },
                )
            }
        }

        private suspend fun startPolling(intervalMs: Long) {
            readMetricsUseCase.startPolling(intervalMs)
            activePollingIntervalMs = intervalMs
            _uiState.update { it.copy(isPolling = true) }
        }

        private suspend fun stopPollingInternal() {
            readMetricsUseCase.stopPolling()
            activePollingIntervalMs = null
            _uiState.update { it.copy(isPolling = false) }
        }

        private fun emitEvent(event: DashboardEvent) {
            viewModelScope.launch {
                _events.emit(event)
            }
        }
    }
