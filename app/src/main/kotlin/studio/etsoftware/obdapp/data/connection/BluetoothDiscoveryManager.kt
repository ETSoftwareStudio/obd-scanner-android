package studio.etsoftware.obdapp.data.connection

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import androidx.core.content.ContextCompat
import androidx.core.content.IntentCompat
import androidx.core.location.LocationManagerCompat
import studio.etsoftware.obdapp.data.logging.LogManager
import studio.etsoftware.obdapp.domain.model.DeviceInfo
import studio.etsoftware.obdapp.domain.model.DeviceType
import studio.etsoftware.obdapp.domain.model.DiscoveryState
import studio.etsoftware.obdapp.domain.model.PairingState
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

@Singleton
class BluetoothDiscoveryManager
    @Inject
    constructor(
        @param:ApplicationContext private val appContext: Context,
        private val logManager: LogManager,
    ) {
        private val bluetoothAccess = BluetoothAccess(appContext)

        private val discoveredDevices = linkedMapOf<String, DeviceInfo>()
        private var receiverRegistered = false
        private var bondReceiverRegistered = false
        private var pairingDeviceAddress: String? = null

        private val _discoveryState = MutableStateFlow<DiscoveryState>(DiscoveryState.Idle)
        val discoveryState: StateFlow<DiscoveryState> = _discoveryState.asStateFlow()

        private val _pairingState = MutableStateFlow<PairingState>(PairingState.Idle)
        val pairingState: StateFlow<PairingState> = _pairingState.asStateFlow()

        private val discoveryReceiver =
            object : BroadcastReceiver() {
                override fun onReceive(
                    context: Context?,
                    intent: Intent?,
                ) {
                    try {
                        when (intent?.action) {
                            BluetoothAdapter.ACTION_DISCOVERY_STARTED -> {
                                _discoveryState.value = DiscoveryState.Discovering(currentDevices())
                                logManager.info("Bluetooth discovery started")
                            }

                            BluetoothDevice.ACTION_FOUND -> {
                                val device = intent.extractBluetoothDevice() ?: return
                                val mappedDevice = mapDevice(device)

                                discoveredDevices[mappedDevice.address] = mappedDevice
                                _discoveryState.value = DiscoveryState.Discovering(currentDevices())
                            }

                            BluetoothAdapter.ACTION_DISCOVERY_FINISHED -> {
                                _discoveryState.value = DiscoveryState.Finished(currentDevices())
                                unregisterReceiverIfNeeded()
                                logManager.info("Bluetooth discovery finished")
                            }
                        }
                    } catch (securityException: SecurityException) {
                        handleDiscoveryError("Bluetooth permissions were revoked during discovery", securityException)
                    }
                }
            }

        private val bondStateReceiver =
            object : BroadcastReceiver() {
                override fun onReceive(
                    context: Context?,
                    intent: Intent?,
                ) {
                    if (intent?.action != BluetoothDevice.ACTION_BOND_STATE_CHANGED) return

                    try {
                        val device = intent.extractBluetoothDevice() ?: return
                        val expectedAddress = pairingDeviceAddress
                        if (expectedAddress != null && device.address != expectedAddress) return

                        val mappedDevice = mapDevice(device)
                        val bondState = intent.getIntExtra(BluetoothDevice.EXTRA_BOND_STATE, BluetoothDevice.ERROR)
                        val previousBondState = intent.getIntExtra(BluetoothDevice.EXTRA_PREVIOUS_BOND_STATE, BluetoothDevice.ERROR)

                        when (bondState) {
                            BluetoothDevice.BOND_BONDING -> {
                                _pairingState.value = PairingState.Pairing(mappedDevice)
                                logManager.info("Pairing in progress for ${mappedDevice.name}")
                            }

                            BluetoothDevice.BOND_BONDED -> {
                                pairingDeviceAddress = null
                                _pairingState.value = PairingState.Paired(mappedDevice)
                                unregisterBondReceiverIfNeeded()
                                logManager.success("Paired with ${mappedDevice.name}")
                            }

                            BluetoothDevice.BOND_NONE -> {
                                if (previousBondState == BluetoothDevice.BOND_BONDING) {
                                    pairingDeviceAddress = null
                                    _pairingState.value =
                                        PairingState.Error(
                                            message = "Pairing was canceled or failed",
                                            device = mappedDevice,
                                        )
                                    unregisterBondReceiverIfNeeded()
                                    logManager.error("Pairing failed for ${mappedDevice.name}")
                                }
                            }
                        }
                    } catch (_: SecurityException) {
                        pairingDeviceAddress = null
                        _pairingState.value = PairingState.Error("Bluetooth permissions were revoked during pairing")
                        unregisterBondReceiverIfNeeded()
                    }
                }
            }

        fun isBluetoothEnabled(): Boolean =
            try {
                bluetoothAccess.adapter?.isEnabled == true
            } catch (_: SecurityException) {
                false
            }

        fun isLocationServicesEnabledForDiscovery(): Boolean {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                return true
            }

            val locationManager = appContext.getSystemService(Context.LOCATION_SERVICE) as? android.location.LocationManager ?: return false
            return LocationManagerCompat.isLocationEnabled(locationManager)
        }

        fun startDiscovery(): Result<Unit> {
            if (discoveryState.value is DiscoveryState.Starting || discoveryState.value is DiscoveryState.Discovering) {
                return Result.success(Unit)
            }

            if (!hasDiscoveryPermission()) {
                val error = Exception("Bluetooth scan permission is required")
                _discoveryState.value = DiscoveryState.Error(error.message ?: "Missing Bluetooth scan permission")
                return Result.failure(error)
            }

            val adapter = bluetoothAccess.adapter ?: run {
                val error = Exception("Bluetooth not available")
                _discoveryState.value = DiscoveryState.Error(error.message ?: "Bluetooth not available")
                return Result.failure(error)
            }

            if (!adapter.isEnabled) {
                val error = Exception("Bluetooth is disabled")
                _discoveryState.value = DiscoveryState.Error(error.message ?: "Bluetooth is disabled")
                return Result.failure(error)
            }

            if (!isLocationServicesEnabledForDiscovery()) {
                val error = Exception("Turn on Location to scan for Bluetooth devices on this Android version")
                _discoveryState.value = DiscoveryState.Error(error.message ?: "Location services are disabled")
                return Result.failure(error)
            }

            return try {
                if (isDiscovering(adapter)) {
                    cancelDiscovery(adapter)
                    unregisterReceiverIfNeeded()
                }

                discoveredDevices.clear()
                _discoveryState.value = DiscoveryState.Starting
                registerReceiverIfNeeded()

                if (!adapter.startDiscovery()) {
                    unregisterReceiverIfNeeded()
                    val error = Exception("Bluetooth discovery could not start. Please try again.")
                    _discoveryState.value = DiscoveryState.Error(error.message ?: "Bluetooth discovery could not start")
                    return Result.failure(error)
                }

                Result.success(Unit)
            } catch (securityException: SecurityException) {
                handleDiscoveryError("Bluetooth discovery requires additional permissions", securityException)
                Result.failure(securityException)
            } catch (exception: Exception) {
                handleDiscoveryError(exception.message ?: "Bluetooth discovery failed", exception)
                Result.failure(exception)
            }
        }

        fun stopDiscovery() {
            val adapter = bluetoothAccess.adapter

            try {
                if (adapter != null && hasDiscoveryPermission() && isDiscovering(adapter)) {
                    cancelDiscovery(adapter)
                }

                if (discoveryState.value is DiscoveryState.Starting || discoveryState.value is DiscoveryState.Discovering) {
                    _discoveryState.value = DiscoveryState.Finished(currentDevices())
                }
                unregisterReceiverIfNeeded()
            } catch (securityException: SecurityException) {
                handleDiscoveryError("Unable to stop Bluetooth discovery", securityException)
            }
        }

        fun clearPairingState() {
            if (_pairingState.value !is PairingState.Pairing) {
                _pairingState.value = PairingState.Idle
            }
        }

        fun pairDevice(device: DeviceInfo): Result<Unit> {
            if (!bluetoothAccess.hasBluetoothConnectPermission()) {
                val error = Exception("Bluetooth connect permission is required to pair devices")
                return Result.failure(error)
            }

            val adapter = bluetoothAccess.adapter ?: return Result.failure(Exception("Bluetooth not available"))

            if (!adapter.isEnabled) {
                return Result.failure(Exception("Bluetooth is disabled"))
            }

            return try {
                stopDiscovery()
                val bluetoothDevice = adapter.getRemoteDevice(device.address)
                if (bondState(bluetoothDevice) == BluetoothDevice.BOND_BONDED) {
                    _pairingState.value = PairingState.Paired(mapDevice(bluetoothDevice))
                    Result.success(Unit)
                } else {
                    pairingDeviceAddress = device.address
                    registerBondReceiverIfNeeded()
                    _pairingState.value = PairingState.Pairing(device)

                    if (bluetoothDevice.createBond()) {
                        logManager.info("Started pairing with ${device.name} (${device.address})")
                        Result.success(Unit)
                    } else {
                        pairingDeviceAddress = null
                        unregisterBondReceiverIfNeeded()
                        _pairingState.value = PairingState.Error("Unable to start Bluetooth pairing", device)
                        Result.failure(Exception("Unable to start Bluetooth pairing"))
                    }
                }
            } catch (securityException: SecurityException) {
                Result.failure(Exception("Bluetooth pairing requires additional permissions", securityException))
            } catch (exception: Exception) {
                Result.failure(exception)
            }
        }

        private fun currentDevices(): List<DeviceInfo> = discoveredDevices.values.toList()

        private fun mapDevice(device: BluetoothDevice): DeviceInfo =
            DeviceInfo(
                address = device.address,
                name = deviceName(device) ?: "Unknown Device",
                type = DeviceType.CLASSIC,
            )

        private fun registerReceiverIfNeeded() {
            if (receiverRegistered) return

            val intentFilter =
                IntentFilter().apply {
                    addAction(BluetoothAdapter.ACTION_DISCOVERY_STARTED)
                    addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED)
                    addAction(BluetoothDevice.ACTION_FOUND)
                }

            ContextCompat.registerReceiver(
                appContext,
                discoveryReceiver,
                intentFilter,
                ContextCompat.RECEIVER_NOT_EXPORTED,
            )

            receiverRegistered = true
        }

        private fun unregisterReceiverIfNeeded() {
            if (!receiverRegistered) return

            try {
                appContext.unregisterReceiver(discoveryReceiver)
            } catch (_: IllegalArgumentException) {
                // Ignore if receiver was already unregistered.
            } finally {
                receiverRegistered = false
            }
        }

        private fun registerBondReceiverIfNeeded() {
            if (bondReceiverRegistered) return

            val intentFilter = IntentFilter(BluetoothDevice.ACTION_BOND_STATE_CHANGED)
            ContextCompat.registerReceiver(
                appContext,
                bondStateReceiver,
                intentFilter,
                ContextCompat.RECEIVER_NOT_EXPORTED,
            )
            bondReceiverRegistered = true
        }

        private fun unregisterBondReceiverIfNeeded() {
            if (!bondReceiverRegistered) return

            try {
                appContext.unregisterReceiver(bondStateReceiver)
            } catch (_: IllegalArgumentException) {
                // Ignore if receiver was already unregistered.
            } finally {
                bondReceiverRegistered = false
            }
        }

        private fun handleDiscoveryError(
            message: String,
            exception: Exception,
        ) {
            logManager.error("$message: ${exception.message}")
            _discoveryState.value = DiscoveryState.Error(message, currentDevices())
            unregisterReceiverIfNeeded()
        }

        private fun hasDiscoveryPermission(): Boolean =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                bluetoothAccess.hasBluetoothScanPermission() && bluetoothAccess.hasBluetoothConnectPermission()
            } else {
                bluetoothAccess.hasLocationPermission()
            }

        private fun Intent.extractBluetoothDevice(): BluetoothDevice? =
            IntentCompat.getParcelableExtra(this, BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)

        private fun deviceName(device: BluetoothDevice): String? {
            if (!bluetoothAccess.hasBluetoothConnectPermission()) {
                throw SecurityException("BLUETOOTH_CONNECT permission is required")
            }
            return try {
                device.name
            } catch (exception: SecurityException) {
                throw exception
            }
        }

        private fun bondState(device: BluetoothDevice): Int {
            if (!bluetoothAccess.hasBluetoothConnectPermission()) {
                throw SecurityException("BLUETOOTH_CONNECT permission is required")
            }
            return try {
                device.bondState
            } catch (exception: SecurityException) {
                throw exception
            }
        }

        private fun isDiscovering(adapter: BluetoothAdapter): Boolean {
            if (!bluetoothAccess.hasBluetoothScanPermission()) {
                throw SecurityException("BLUETOOTH_SCAN permission is required")
            }
            return try {
                adapter.isDiscovering
            } catch (exception: SecurityException) {
                throw exception
            }
        }

        private fun cancelDiscovery(adapter: BluetoothAdapter) {
            if (!bluetoothAccess.hasBluetoothScanPermission()) {
                throw SecurityException("BLUETOOTH_SCAN permission is required")
            }
            try {
                adapter.cancelDiscovery()
            } catch (exception: SecurityException) {
                throw exception
            }
        }
    }
