package com.eltonvs.obdapp.ui.feature.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.eltonvs.obdapp.domain.model.ConnectionState
import com.eltonvs.obdapp.domain.model.DeviceInfo
import com.eltonvs.obdapp.domain.model.DeviceType
import com.eltonvs.obdapp.domain.repository.ObdRepository
import com.eltonvs.obdapp.domain.usecase.ConnectDeviceUseCase
import com.eltonvs.obdapp.domain.usecase.DisconnectUseCase
import com.eltonvs.obdapp.domain.usecase.ReadMetricsUseCase
import com.eltonvs.obdapp.util.LogManager
import com.eltonvs.obdapp.util.PreferencesManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

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
        val logManager: LogManager,
    ) : ViewModel() {
        private val _uiState = MutableStateFlow(DashboardUiState())
        val uiState: StateFlow<DashboardUiState> = _uiState.asStateFlow()

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
                readMetricsUseCase().collect { metric ->
                    when (metric.name) {
                        "Speed" -> _uiState.update { it.copy(speed = metric.value) }
                        "RPM" -> _uiState.update { it.copy(rpm = metric.value) }
                        "Throttle" -> _uiState.update { it.copy(throttle = metric.value) }
                        "Coolant" -> _uiState.update { it.copy(coolantTemp = metric.value) }
                        "Intake" -> _uiState.update { it.copy(intakeTemp = metric.value) }
                        "MAF" -> _uiState.update { it.copy(maf = metric.value) }
                        "Fuel" -> _uiState.update { it.copy(fuel = metric.value) }
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
    }
