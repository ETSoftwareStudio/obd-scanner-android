package com.eltonvs.obdapp.ui.feature.dashboard

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.eltonvs.obdapp.domain.model.ConnectionState
import com.eltonvs.obdapp.domain.model.DeviceInfo
import com.eltonvs.obdapp.domain.model.DeviceType
import com.eltonvs.obdapp.domain.repository.ObdRepository
import com.eltonvs.obdapp.domain.usecase.ClearTelemetryUseCase
import com.eltonvs.obdapp.domain.usecase.ConnectDeviceUseCase
import com.eltonvs.obdapp.domain.usecase.DisconnectUseCase
import com.eltonvs.obdapp.domain.usecase.ObserveTelemetryEventsUseCase
import com.eltonvs.obdapp.domain.usecase.ReadMetricsUseCase
import com.eltonvs.obdapp.util.LogEntry
import com.eltonvs.obdapp.util.LogExportFormatter
import com.eltonvs.obdapp.util.LogExporter
import com.eltonvs.obdapp.util.LogManager
import com.eltonvs.obdapp.util.PreferencesManager
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

        init {
            observeConnectionState()
            observeMetrics()
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
                    _uiState.update { it.copy(connectionState = state) }
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

        private fun startPollingIfNeeded() {
            if (_uiState.value.isPolling) return
            viewModelScope.launch {
                val interval = preferencesManager.pollingInterval.first()
                readMetricsUseCase.startPolling(interval)
                _uiState.update { it.copy(isPolling = true) }
            }
        }

        fun stopPolling() {
            viewModelScope.launch {
                readMetricsUseCase.stopPolling()
                _uiState.update { it.copy(isPolling = false) }
            }
        }

        fun disconnect() {
            viewModelScope.launch {
                stopPolling()
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

        private fun emitEvent(event: DashboardEvent) {
            viewModelScope.launch {
                _events.emit(event)
            }
        }
    }
