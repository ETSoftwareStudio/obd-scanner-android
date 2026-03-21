package com.eltonvs.obdapp.data.repository

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import com.eltonvs.obdapp.data.connection.BluetoothDiscoveryManager
import com.eltonvs.obdapp.data.connection.ObdTransport
import com.eltonvs.obdapp.domain.model.ConnectionState
import com.eltonvs.obdapp.domain.model.DeviceInfo
import com.eltonvs.obdapp.domain.model.DeviceType
import com.eltonvs.obdapp.domain.model.DiagnosticInfo
import com.eltonvs.obdapp.domain.model.DiscoveryState
import com.eltonvs.obdapp.domain.model.PairingState
import com.eltonvs.obdapp.domain.model.TroubleCode
import com.eltonvs.obdapp.domain.model.TroubleCodeType
import com.eltonvs.obdapp.domain.model.VehicleMetric
import com.eltonvs.obdapp.domain.repository.ObdRepository
import com.eltonvs.obdapp.util.LogManager
import com.github.eltonvs.obd.command.Switcher
import com.github.eltonvs.obd.command.at.ResetAdapterCommand
import com.github.eltonvs.obd.command.at.SetEchoCommand
import com.github.eltonvs.obd.command.control.ResetTroubleCodesCommand
import com.github.eltonvs.obd.command.control.TroubleCodesCommand
import com.github.eltonvs.obd.command.control.VINCommand
import com.github.eltonvs.obd.command.engine.MassAirFlowCommand
import com.github.eltonvs.obd.command.engine.RPMCommand
import com.github.eltonvs.obd.command.engine.SpeedCommand
import com.github.eltonvs.obd.command.engine.ThrottlePositionCommand
import com.github.eltonvs.obd.command.fuel.FuelLevelCommand
import com.github.eltonvs.obd.command.temperature.AirIntakeTemperatureCommand
import com.github.eltonvs.obd.command.temperature.EngineCoolantTemperatureCommand
import com.github.eltonvs.obd.connection.ObdDeviceConnection
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Singleton
class ObdRepositoryImpl
    @Inject
    constructor(
        @ApplicationContext private val appContext: Context,
        private val transport: ObdTransport,
        private val discoveryManager: BluetoothDiscoveryManager,
        private val logManager: LogManager,
    ) : ObdRepository {
        private var obdConnection: ObdDeviceConnection? = null
        private val scope = CoroutineScope(Dispatchers.IO)
        private var pollingJob: Job? = null

        private val _connectionState = MutableStateFlow<ConnectionState>(ConnectionState.Disconnected)
        override val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()
        override val discoveryState: StateFlow<DiscoveryState> = discoveryManager.discoveryState
        override val pairingState: StateFlow<PairingState> = discoveryManager.pairingState

        private val _metricsFlow = MutableSharedFlow<VehicleMetric>(replay = 1, extraBufferCapacity = 64)
        private val dtcRegex = Regex("[PCBU][0-3][0-9A-F]{3}")

        override val vehicleMetrics: Flow<VehicleMetric> = _metricsFlow.asSharedFlow()

        override fun isBluetoothEnabled(): Boolean = discoveryManager.isBluetoothEnabled()

        override fun isLocationServicesEnabledForDiscovery(): Boolean =
            discoveryManager.isLocationServicesEnabledForDiscovery()

        override fun startDiscovery(): Result<Unit> = discoveryManager.startDiscovery()

        override fun stopDiscovery() = discoveryManager.stopDiscovery()

        override fun pairDevice(device: DeviceInfo): Result<Unit> = discoveryManager.pairDevice(device)

        override fun clearPairingState() = discoveryManager.clearPairingState()

        @Suppress("DEPRECATION")
        @SuppressLint("MissingPermission")
        override suspend fun getPairedDevices(): List<DeviceInfo> =
            withContext(Dispatchers.IO) {
                if (!hasBluetoothConnectPermission()) {
                    logManager.error("Missing BLUETOOTH_CONNECT permission")
                    return@withContext emptyList()
                }

                val adapter = BluetoothAdapter.getDefaultAdapter() ?: return@withContext emptyList()
                if (!adapter.isEnabled) {
                    logManager.error("Bluetooth is disabled")
                    return@withContext emptyList()
                }

                try {
                    adapter.bondedDevices?.map { bluetoothDevice ->
                        DeviceInfo(
                            address = bluetoothDevice.address,
                            name = bluetoothDevice.name ?: "Unknown Device",
                            type = DeviceType.CLASSIC,
                        )
                    } ?: emptyList()
                } catch (_: SecurityException) {
                    logManager.error("Unable to read paired devices without BLUETOOTH_CONNECT permission")
                    emptyList()
                }
            }

        override suspend fun connect(device: DeviceInfo): Result<Unit> {
            stopDiscovery()
            _connectionState.value = ConnectionState.Connecting
            logManager.info("Connecting to ${device.name} (${device.address})...")

            if (!hasBluetoothConnectPermission()) {
                val error = Exception("BLUETOOTH_CONNECT permission is required")
                _connectionState.value = ConnectionState.Error(error.message ?: "Missing permission")
                logManager.error(error.message ?: "Missing BLUETOOTH_CONNECT permission")
                return Result.failure(error)
            }

            val connectionResult = transport.connect(device)

            if (connectionResult.isFailure) {
                val errorMsg = connectionResult.exceptionOrNull()?.message ?: "Connection failed"
                _connectionState.value = ConnectionState.Error(errorMsg)
                logManager.error("Connection failed: $errorMsg")
                return Result.failure(connectionResult.exceptionOrNull() ?: Exception("Connection failed"))
            }

            val inputStream = transport.getInputStream()
            val outputStream = transport.getOutputStream()

            if (inputStream == null || outputStream == null) {
                val error = Exception("Failed to get streams")
                _connectionState.value = ConnectionState.Error(error.message ?: "Failed to get streams")
                logManager.error("Failed to get Bluetooth streams")
                return Result.failure(error)
            }

            obdConnection = ObdDeviceConnection(inputStream, outputStream)

            try {
                logManager.command("ATZ (Reset adapter)")
                obdConnection?.run(ResetAdapterCommand())
                logManager.command("ATE0 (Echo off)")
                obdConnection?.run(SetEchoCommand(Switcher.OFF))
                _connectionState.value = ConnectionState.Connected
                logManager.success("Connected to ${device.name}")
                return Result.success(Unit)
            } catch (e: Exception) {
                _connectionState.value = ConnectionState.Error(e.message ?: "Failed to initialize OBD")
                logManager.error("OBD initialization failed: ${e.message}")
                return Result.failure(e)
            }
        }

        override suspend fun disconnect() {
            logManager.info("Disconnecting...")
            pollingJob?.cancel()
            pollingJob = null
            obdConnection = null
            transport.disconnect()
            _connectionState.value = ConnectionState.Disconnected
            logManager.info("Disconnected")
        }

        override suspend fun readDiagnosticInfo(): Result<DiagnosticInfo> =
            withContext(Dispatchers.IO) {
                val connection = obdConnection ?: return@withContext Result.failure(Exception("Not connected"))

                try {
                    val vin = connection.run(VINCommand())
                    val troubleCodes = connection.run(TroubleCodesCommand())

                    val codes = parseDTCs(troubleCodes.value)

                    Result.success(
                        DiagnosticInfo(
                            vin = vin.value,
                            troubleCodes =
                                codes.map { code ->
                                    TroubleCode(code, getDTCDescription(code), TroubleCodeType.CURRENT)
                                },
                            milStatus = codes.isNotEmpty(),
                            dtcCount = codes.size,
                        ),
                    )
                } catch (e: Exception) {
                    Result.failure(e)
                }
            }

        override suspend fun clearTroubleCodes(): Result<Unit> =
            withContext(Dispatchers.IO) {
                val connection = obdConnection ?: return@withContext Result.failure(Exception("Not connected"))

                try {
                    logManager.command("04 (Clear trouble codes)")
                    connection.run(ResetTroubleCodesCommand())
                    logManager.success("Trouble codes clear command sent")
                    Result.success(Unit)
                } catch (e: Exception) {
                    logManager.error("Failed to clear trouble codes: ${e.message}")
                    Result.failure(e)
                }
            }

        override suspend fun startPolling(intervalMs: Long) {
            pollingJob?.cancel()
            pollingJob =
                scope.launch {
                    while (isActive) {
                        if (obdConnection != null && transport.isConnected()) {
                            readMetrics()
                        } else {
                            _connectionState.value = ConnectionState.Disconnected
                            break
                        }
                        delay(intervalMs)
                    }
                }
        }

        override suspend fun stopPolling() {
            pollingJob?.cancel()
            pollingJob = null
        }

        private suspend fun readMetrics() {
            val connection = obdConnection ?: return

            try {
                logManager.command("010D (Speed)")
                val speed = connection.run(SpeedCommand())
                logManager.response("010D: ${speed.value}")

                logManager.command("010C (RPM)")
                val rpm = connection.run(RPMCommand())
                logManager.response("010C: ${rpm.value}")

                logManager.command("0105 (Coolant)")
                val coolant = connection.run(EngineCoolantTemperatureCommand())
                logManager.response("0105: ${coolant.value}")

                logManager.command("010F (Intake Air)")
                val intakeAir = connection.run(AirIntakeTemperatureCommand())
                logManager.response("010F: ${intakeAir.value}")

                logManager.command("0110 (MAF)")
                val maf = connection.run(MassAirFlowCommand())
                logManager.response("0110: ${maf.value}")

                logManager.command("0111 (Throttle)")
                val throttle = connection.run(ThrottlePositionCommand())
                logManager.response("0111: ${throttle.value}")

                logManager.command("012F (Fuel Level)")
                val fuel = connection.run(FuelLevelCommand())
                logManager.response("012F: ${fuel.value}")

                _metricsFlow.emit(
                    VehicleMetric(
                        name = "Speed",
                        value = speed.value,
                        unit = speed.unit,
                        minValue = 0f,
                        maxValue = 200f,
                    ),
                )

                _metricsFlow.emit(
                    VehicleMetric(
                        name = "RPM",
                        value = rpm.value,
                        unit = rpm.unit,
                        minValue = 0f,
                        maxValue = 8000f,
                    ),
                )

                _metricsFlow.emit(
                    VehicleMetric(
                        name = "Coolant",
                        value = coolant.value,
                        unit = coolant.unit,
                        minValue = -40f,
                        maxValue = 215f,
                    ),
                )

                _metricsFlow.emit(
                    VehicleMetric(
                        name = "Intake",
                        value = intakeAir.value,
                        unit = intakeAir.unit,
                        minValue = -40f,
                        maxValue = 215f,
                    ),
                )

                _metricsFlow.emit(
                    VehicleMetric(
                        name = "MAF",
                        value = maf.value,
                        unit = maf.unit,
                        minValue = 0f,
                        maxValue = 655.35f,
                    ),
                )

                _metricsFlow.emit(
                    VehicleMetric(
                        name = "Throttle",
                        value = throttle.value,
                        unit = throttle.unit,
                        minValue = 0f,
                        maxValue = 100f,
                    ),
                )

                _metricsFlow.emit(
                    VehicleMetric(
                        name = "Fuel",
                        value = fuel.value,
                        unit = fuel.unit,
                        minValue = 0f,
                        maxValue = 100f,
                    ),
                )
            } catch (e: Exception) {
                val errorMsg = e.message?.takeIf { it.isNotBlank() } ?: e::class.simpleName?.toString() ?: "Unknown error"
                logManager.error("Read error: $errorMsg")
            }
        }

        private fun parseDTCs(rawValue: String): List<String> {
            if (rawValue.isBlank()) return emptyList()

            val normalized = rawValue.uppercase()
            if (normalized.contains("NO DATA") || normalized.contains("NODATA")) {
                return emptyList()
            }

            val directCodes =
                dtcRegex
                    .findAll(normalized)
                    .map { it.value }
                    .filterNot { it == "P0000" }
                    .distinct()
                    .toList()

            if (directCodes.isNotEmpty()) {
                return directCodes
            }

            val hexPayload = normalized.filter { it.isDigit() || it in 'A'..'F' }
            if (hexPayload.length < 4) {
                return emptyList()
            }

            return hexPayload
                .chunked(4)
                .mapNotNull { chunk ->
                    if (chunk.length < 4 || chunk == "0000") {
                        null
                    } else {
                        decodeDtcFromHex(chunk)
                    }
                }
                .distinct()
        }

        private fun decodeDtcFromHex(rawCode: String): String? {
            val value = rawCode.toIntOrNull(16) ?: return null

            val system =
                when ((value and 0xC000) shr 14) {
                    0 -> 'P'
                    1 -> 'C'
                    2 -> 'B'
                    else -> 'U'
                }

            val code =
                buildString(5) {
                    append(system)
                    append((value and 0x3000) shr 12)
                    append(((value and 0x0F00) shr 8).toString(16).uppercase())
                    append(((value and 0x00F0) shr 4).toString(16).uppercase())
                    append((value and 0x000F).toString(16).uppercase())
                }

            return code.takeIf { it != "P0000" && dtcRegex.matches(it) }
        }

        private fun hasBluetoothConnectPermission(): Boolean {
            return Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
                ContextCompat.checkSelfPermission(
                    appContext,
                    Manifest.permission.BLUETOOTH_CONNECT,
                ) == PackageManager.PERMISSION_GRANTED
        }

        private fun getDTCDescription(code: String): String {
            return "Diagnostic Trouble Code $code"
        }
    }
