package studio.etsoftware.obdapp.ui.feature.connection

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import studio.etsoftware.obdapp.domain.model.ConnectionState
import studio.etsoftware.obdapp.domain.model.DeviceInfo
import studio.etsoftware.obdapp.domain.model.DeviceType
import studio.etsoftware.obdapp.domain.model.DiscoveryState
import studio.etsoftware.obdapp.domain.model.PairingState
import studio.etsoftware.obdapp.domain.repository.ObdRepository
import studio.etsoftware.obdapp.domain.usecase.ConnectDeviceUseCase
import studio.etsoftware.obdapp.domain.usecase.GetPairedDevicesUseCase
import studio.etsoftware.obdapp.util.PreferencesManager
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ConnectionUiState(
    val pairedDevices: List<DeviceInfo> = emptyList(),
    val nearbyDevices: List<DeviceInfo> = emptyList(),
    val selectedDevice: DeviceInfo? = null,
    val isLoading: Boolean = false,
    val isLoadingPairedDevices: Boolean = false,
    val pairingDeviceAddress: String? = null,
    val discoveryState: DiscoveryState = DiscoveryState.Idle,
    val isBluetoothEnabled: Boolean = true,
    val isLocationServicesEnabledForDiscovery: Boolean = true,
    val message: String? = null,
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
            observeConnectionState()
            observeDiscoveryState()
            observePairingState()
            refreshSystemState()
            loadPairedDevices()
            loadLastDevice()
        }

        override fun onCleared() {
            repository.stopDiscovery()
            super.onCleared()
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

        private fun observeDiscoveryState() {
            viewModelScope.launch {
                repository.discoveryState.collect { state ->
                    _uiState.update { currentState ->
                        currentState.copy(
                            discoveryState = state,
                            nearbyDevices = filterNearbyDevices(state.devices(), currentState.pairedDevices),
                            error = if (state is DiscoveryState.Error) state.message else currentState.error,
                        )
                    }
                }
            }
        }

        private fun observePairingState() {
            viewModelScope.launch {
                repository.pairingState.collect { state ->
                    when (state) {
                        PairingState.Idle -> {
                            _uiState.update { it.copy(pairingDeviceAddress = null) }
                        }

                        is PairingState.Pairing -> {
                            _uiState.update {
                                it.copy(
                                    pairingDeviceAddress = state.device.address,
                                    message = "Finish pairing ${state.device.name} in the Android Bluetooth dialog",
                                )
                            }
                        }

                        is PairingState.Paired -> {
                            _uiState.update {
                                it.copy(
                                    pairingDeviceAddress = null,
                                    selectedDevice = state.device,
                                    message = "${state.device.name} paired successfully",
                                )
                            }
                            loadPairedDevices()
                            repository.clearPairingState()
                        }

                        is PairingState.Error -> {
                            _uiState.update {
                                it.copy(
                                    pairingDeviceAddress = null,
                                    error = state.message,
                                )
                            }
                            repository.clearPairingState()
                        }
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

        fun refreshSystemState() {
            _uiState.update {
                it.copy(
                    isBluetoothEnabled = repository.isBluetoothEnabled(),
                    isLocationServicesEnabledForDiscovery = repository.isLocationServicesEnabledForDiscovery(),
                )
            }
        }

        fun loadPairedDevices() {
            viewModelScope.launch {
                _uiState.update { it.copy(isLoadingPairedDevices = true) }
                try {
                    val devices = getPairedDevicesUseCase()
                    _uiState.update { currentState ->
                        currentState.copy(
                            pairedDevices = devices,
                            nearbyDevices = filterNearbyDevices(currentState.discoveryState.devices(), devices),
                            selectedDevice = updateSelectedDevice(currentState.selectedDevice, devices),
                            isLoadingPairedDevices = false,
                            isBluetoothEnabled = repository.isBluetoothEnabled(),
                            isLocationServicesEnabledForDiscovery = repository.isLocationServicesEnabledForDiscovery(),
                        )
                    }
                } catch (e: Exception) {
                    _uiState.update {
                        it.copy(
                            error = e.message,
                            isLoadingPairedDevices = false,
                            isBluetoothEnabled = repository.isBluetoothEnabled(),
                            isLocationServicesEnabledForDiscovery = repository.isLocationServicesEnabledForDiscovery(),
                        )
                    }
                }
            }
        }

        fun startDiscovery() {
            refreshSystemState()
            repository.startDiscovery().exceptionOrNull()?.let { error ->
                _uiState.update { it.copy(error = error.message) }
            }
        }

        fun stopDiscovery() {
            repository.stopDiscovery()
        }

        fun selectDevice(device: DeviceInfo) {
            _uiState.update { it.copy(selectedDevice = device) }
        }

        fun pairDevice(device: DeviceInfo): Boolean {
            _uiState.update {
                it.copy(
                    pairingDeviceAddress = device.address,
                    message = null,
                    error = null,
                )
            }

            return repository.pairDevice(device).fold(
                onSuccess = { true },
                onFailure = { error ->
                    _uiState.update { it.copy(pairingDeviceAddress = null, error = error.message) }
                    repository.clearPairingState()
                    false
                },
            )
        }

        fun connect() {
            val device = _uiState.value.selectedDevice ?: return
            repository.stopDiscovery()

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

        fun clearMessage() {
            _uiState.update { it.copy(message = null) }
        }

        private fun filterNearbyDevices(
            discoveredDevices: List<DeviceInfo>,
            pairedDevices: List<DeviceInfo>,
        ): List<DeviceInfo> {
            val pairedAddresses = pairedDevices.map { it.address }.toSet()
            return discoveredDevices.filterNot { it.address in pairedAddresses }
        }

        private fun updateSelectedDevice(
            selectedDevice: DeviceInfo?,
            pairedDevices: List<DeviceInfo>,
        ): DeviceInfo? {
            val selectedAddress = selectedDevice?.address ?: return selectedDevice
            return pairedDevices.firstOrNull { it.address == selectedAddress } ?: selectedDevice
        }

        private fun DiscoveryState.devices(): List<DeviceInfo> {
            return when (this) {
                is DiscoveryState.Discovering -> devices
                is DiscoveryState.Error -> devices
                is DiscoveryState.Finished -> devices
                DiscoveryState.Idle,
                DiscoveryState.Starting,
                -> emptyList()
            }
        }
    }
