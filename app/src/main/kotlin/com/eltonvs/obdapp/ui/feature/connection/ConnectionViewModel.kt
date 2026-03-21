package com.eltonvs.obdapp.ui.feature.connection

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.eltonvs.obdapp.domain.model.ConnectionState
import com.eltonvs.obdapp.domain.model.DeviceInfo
import com.eltonvs.obdapp.domain.model.DeviceType
import com.eltonvs.obdapp.domain.repository.ObdRepository
import com.eltonvs.obdapp.domain.usecase.ConnectDeviceUseCase
import com.eltonvs.obdapp.domain.usecase.GetPairedDevicesUseCase
import com.eltonvs.obdapp.util.PreferencesManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ConnectionUiState(
    val devices: List<DeviceInfo> = emptyList(),
    val selectedDevice: DeviceInfo? = null,
    val isLoading: Boolean = false,
    val isScanning: Boolean = false,
    val error: String? = null,
    val connectionState: ConnectionState = ConnectionState.Disconnected,
)

@HiltViewModel
class ConnectionViewModel
    @Inject
    constructor(
        private val getPairedDevicesUseCase: GetPairedDevicesUseCase,
        private val connectDeviceUseCase: ConnectDeviceUseCase,
        private val repository: ObdRepository,
        private val preferencesManager: PreferencesManager,
    ) : ViewModel() {
        private val _uiState = MutableStateFlow(ConnectionUiState())
        val uiState: StateFlow<ConnectionUiState> = _uiState.asStateFlow()

        init {
            loadPairedDevices()
            observeConnectionState()
            loadLastDevice()
        }

        private fun observeConnectionState() {
            viewModelScope.launch {
                repository.connectionState.collect { state ->
                    _uiState.update { it.copy(connectionState = state) }
                    if (state is ConnectionState.Connected) {
                        preferencesManager.setWasConnected(true)
                    } else if (state is ConnectionState.Disconnected) {
                        preferencesManager.setWasConnected(false)
                    }
                }
            }
        }

        private fun loadLastDevice() {
            viewModelScope.launch {
                val address = preferencesManager.lastDeviceAddress.first()
                val name = preferencesManager.lastDeviceName.first()
                if (address != null && name != null) {
                    _uiState.update {
                        it.copy(
                            selectedDevice = DeviceInfo(address, name, DeviceType.CLASSIC),
                        )
                    }
                }
            }
        }

        fun loadPairedDevices() {
            viewModelScope.launch {
                _uiState.update { it.copy(isScanning = true) }
                try {
                    val devices = getPairedDevicesUseCase()
                    _uiState.update { it.copy(devices = devices, isScanning = false) }
                } catch (e: Exception) {
                    _uiState.update { it.copy(error = e.message, isScanning = false) }
                }
            }
        }

        fun selectDevice(device: DeviceInfo) {
            _uiState.update { it.copy(selectedDevice = device) }
        }

        fun connect() {
            val device = _uiState.value.selectedDevice ?: return

            viewModelScope.launch {
                _uiState.update { it.copy(isLoading = true, error = null) }

                connectDeviceUseCase(device).fold(
                    onSuccess = {
                        preferencesManager.setLastDevice(device.address, device.name)
                        _uiState.update { it.copy(isLoading = false) }
                    },
                    onFailure = { e ->
                        _uiState.update { it.copy(error = e.message, isLoading = false) }
                    },
                )
            }
        }

        fun clearError() {
            _uiState.update { it.copy(error = null) }
        }
    }
