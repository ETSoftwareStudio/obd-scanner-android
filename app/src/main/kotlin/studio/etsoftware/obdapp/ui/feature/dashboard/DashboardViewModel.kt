package studio.etsoftware.obdapp.ui.feature.dashboard

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import studio.etsoftware.obdapp.domain.model.ConnectionState
import studio.etsoftware.obdapp.domain.model.DebugLogEntry
import studio.etsoftware.obdapp.domain.usecase.BuildLogExportTextUseCase
import studio.etsoftware.obdapp.domain.usecase.ClearDebugLogsUseCase
import studio.etsoftware.obdapp.domain.usecase.ClearTelemetryUseCase
import studio.etsoftware.obdapp.domain.usecase.ConnectDeviceUseCase
import studio.etsoftware.obdapp.domain.usecase.DisconnectUseCase
import studio.etsoftware.obdapp.domain.usecase.ExportLogsUseCase
import studio.etsoftware.obdapp.domain.usecase.GetSuggestedLogFileNameUseCase
import studio.etsoftware.obdapp.domain.usecase.ObserveAutoConnectUseCase
import studio.etsoftware.obdapp.domain.usecase.ObserveConnectionStateUseCase
import studio.etsoftware.obdapp.domain.usecase.ObserveDashboardMetricsUseCase
import studio.etsoftware.obdapp.domain.usecase.ObserveDebugLogsUseCase
import studio.etsoftware.obdapp.domain.usecase.ObserveLastDeviceUseCase
import studio.etsoftware.obdapp.domain.usecase.ObservePollingIntervalUseCase
import studio.etsoftware.obdapp.domain.usecase.ObserveTelemetryEventsUseCase
import studio.etsoftware.obdapp.domain.usecase.ObserveWasConnectedUseCase
import studio.etsoftware.obdapp.domain.usecase.StartDashboardPollingUseCase
import studio.etsoftware.obdapp.domain.usecase.StopDashboardPollingUseCase
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
        private val observeDashboardMetricsUseCase: ObserveDashboardMetricsUseCase,
        private val startDashboardPollingUseCase: StartDashboardPollingUseCase,
        private val stopDashboardPollingUseCase: StopDashboardPollingUseCase,
        private val disconnectUseCase: DisconnectUseCase,
        private val connectDeviceUseCase: ConnectDeviceUseCase,
        private val observeConnectionStateUseCase: ObserveConnectionStateUseCase,
        private val observeAutoConnectUseCase: ObserveAutoConnectUseCase,
        private val observeWasConnectedUseCase: ObserveWasConnectedUseCase,
        private val observeLastDeviceUseCase: ObserveLastDeviceUseCase,
        private val observeDebugLogsUseCase: ObserveDebugLogsUseCase,
        private val clearDebugLogsUseCase: ClearDebugLogsUseCase,
        private val getSuggestedLogFileNameUseCase: GetSuggestedLogFileNameUseCase,
        private val buildLogExportTextUseCase: BuildLogExportTextUseCase,
        private val exportLogsUseCase: ExportLogsUseCase,
        private val observePollingIntervalUseCase: ObservePollingIntervalUseCase,
        private val observeTelemetryEventsUseCase: ObserveTelemetryEventsUseCase,
        private val clearTelemetryUseCase: ClearTelemetryUseCase,
    ) : ViewModel() {
        private val _uiState = MutableStateFlow(DashboardUiState())
        val uiState: StateFlow<DashboardUiState> = _uiState.asStateFlow()

        val logs: StateFlow<List<DebugLogEntry>> = observeDebugLogsUseCase()
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
                val autoConnect = observeAutoConnectUseCase().first()
                val wasConnected = observeWasConnectedUseCase().first()
                val lastDevice = observeLastDeviceUseCase().first()
                val currentState = observeConnectionStateUseCase().value

                _uiState.update { it.copy(autoConnectEnabled = autoConnect) }

                if (currentState is ConnectionState.Connected) {
                    startPollingIfNeeded()
                    return@launch
                }

                if (autoConnect && wasConnected && lastDevice != null) {
                    connectDeviceUseCase(lastDevice)
                }
            }
        }

        private fun observeConnectionState() {
            viewModelScope.launch {
                observeConnectionStateUseCase().collect { state ->
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
                observeDashboardMetricsUseCase().collect { snapshot ->
                    _uiState.update {
                        it.copy(
                            speed = snapshot.speed.displayValue(),
                            rpm = snapshot.rpm.displayValue(),
                            throttle = snapshot.throttle.displayValue(),
                            coolantTemp = snapshot.coolantTemp.displayValue(),
                            intakeTemp = snapshot.intakeTemp.displayValue(),
                            maf = snapshot.maf.displayValue(),
                            fuel = snapshot.fuel.displayValue(),
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
                        observeConnectionStateUseCase().value is ConnectionState.Connected &&
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
            viewModelScope.launch {
                clearDebugLogsUseCase()
                clearTelemetryUseCase()
            }
        }

        fun getSuggestedLogFileName(): String = getSuggestedLogFileNameUseCase()

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
                    buildLogExportTextUseCase(
                        logs = logSnapshot,
                        telemetryEvents = telemetryEvents.value,
                    )
                exportLogsUseCase(uri.toString(), exportText).fold(
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
            startDashboardPollingUseCase(intervalMs)
            activePollingIntervalMs = intervalMs
            _uiState.update { it.copy(isPolling = true) }
        }

        private suspend fun stopPollingInternal() {
            stopDashboardPollingUseCase()
            activePollingIntervalMs = null
            _uiState.update { it.copy(isPolling = false) }
        }

        private fun emitEvent(event: DashboardEvent) {
            viewModelScope.launch {
                _events.emit(event)
            }
        }

        private fun String.displayValue(): String = ifBlank { "--" }
    }
