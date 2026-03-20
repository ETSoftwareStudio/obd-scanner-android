package com.eltonvs.obdapp.data.repository

import android.bluetooth.BluetoothAdapter
import com.eltonvs.obdapp.data.connection.ObdTransport
import com.eltonvs.obdapp.domain.model.ConnectionState
import com.eltonvs.obdapp.domain.model.DeviceInfo
import com.eltonvs.obdapp.domain.model.DeviceType
import com.eltonvs.obdapp.domain.model.DiagnosticInfo
import com.eltonvs.obdapp.domain.model.TroubleCode
import com.eltonvs.obdapp.domain.model.TroubleCodeType
import com.eltonvs.obdapp.domain.model.VehicleMetric
import com.eltonvs.obdapp.domain.repository.ObdRepository
import com.eltonvs.obdapp.util.LogManager
import com.github.eltonvs.obd.command.Switcher
import com.github.eltonvs.obd.command.at.ResetAdapterCommand
import com.github.eltonvs.obd.command.at.SetEchoCommand
import com.github.eltonvs.obd.command.control.TroubleCodesCommand
import com.github.eltonvs.obd.command.control.VINCommand
import com.github.eltonvs.obd.command.engine.RPMCommand
import com.github.eltonvs.obd.command.engine.ThrottlePositionCommand
import com.github.eltonvs.obd.command.fuel.FuelLevelCommand
import com.github.eltonvs.obd.connection.ObdDeviceConnection
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ObdRepositoryImpl
    @Inject
    constructor(
        private val transport: ObdTransport,
        private val logManager: LogManager,
    ) : ObdRepository {
        private var obdConnection: ObdDeviceConnection? = null
        private val scope = CoroutineScope(Dispatchers.IO)
        private var pollingJob: Job? = null

        private val _connectionState = MutableStateFlow<ConnectionState>(ConnectionState.Disconnected)
        override val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()

        private val _metricsFlow = MutableStateFlow<VehicleMetric?>(null)

        override val vehicleMetrics: Flow<VehicleMetric> =
            flow {
                _metricsFlow.collect { metric ->
                    metric?.let { emit(it) }
                }
            }

        @Suppress("DEPRECATION")
        override suspend fun getPairedDevices(): List<DeviceInfo> =
            withContext(Dispatchers.IO) {
                val adapter = BluetoothAdapter.getDefaultAdapter() ?: return@withContext emptyList()

                adapter.bondedDevices?.map { bluetoothDevice ->
                    DeviceInfo(
                        address = bluetoothDevice.address,
                        name = bluetoothDevice.name ?: "Unknown Device",
                        type = DeviceType.CLASSIC,
                    )
                } ?: emptyList()
            }

        override suspend fun connect(device: DeviceInfo): Result<Unit> {
            _connectionState.value = ConnectionState.Connecting
            logManager.info("Connecting to ${device.name} (${device.address})...")

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
                logManager.command("010C (RPM)")
                val rpm = connection.run(RPMCommand())
                logManager.response("010C: ${rpm.value}")

                logManager.command("0111 (Throttle)")
                val throttle = connection.run(ThrottlePositionCommand())
                logManager.response("0111: ${throttle.value}")

                logManager.command("012F (Fuel Level)")
                val fuel = connection.run(FuelLevelCommand())
                logManager.response("012F: ${fuel.value}")

                _metricsFlow.value =
                    VehicleMetric(
                        name = "RPM",
                        value = rpm.value,
                        unit = rpm.unit,
                        minValue = 0f,
                        maxValue = 8000f,
                    )

                _metricsFlow.value =
                    VehicleMetric(
                        name = "Throttle",
                        value = throttle.value,
                        unit = throttle.unit,
                        minValue = 0f,
                        maxValue = 100f,
                    )

                _metricsFlow.value =
                    VehicleMetric(
                        name = "Fuel",
                        value = fuel.value,
                        unit = fuel.unit,
                        minValue = 0f,
                        maxValue = 100f,
                    )
            } catch (e: Exception) {
                val errorMsg = e.message?.takeIf { it.isNotBlank() } ?: e::class.simpleName?.toString() ?: "Unknown error"
                logManager.error("Read error: $errorMsg")
            }
        }

        private fun parseDTCs(rawValue: String): List<String> {
            if (rawValue.isBlank()) return emptyList()

            return rawValue
                .replace(" ", "")
                .chunked(2)
                .filter { it != "00" }
                .map { dtc ->
                    val first = dtc.first()
                    when (first) {
                        '0' -> "P0${dtc[1]}"
                        '1' -> "P1${dtc[1]}"
                        '2' -> "P2${dtc[1]}"
                        '3' -> "P3${dtc[1]}"
                        '4' -> "C0${dtc[1]}"
                        '5' -> "C1${dtc[1]}"
                        '6' -> "C2${dtc[1]}"
                        '7' -> "C3${dtc[1]}"
                        '8' -> "B0${dtc[1]}"
                        '9' -> "B1${dtc[1]}"
                        'A' -> "B2${dtc[1]}"
                        'B' -> "B3${dtc[1]}"
                        'C' -> "U0${dtc[1]}"
                        'D' -> "U1${dtc[1]}"
                        'E' -> "U2${dtc[1]}"
                        'F' -> "U3${dtc[1]}"
                        else -> dtc
                    }
                }
        }

        private fun getDTCDescription(code: String): String {
            return "Diagnostic Trouble Code $code"
        }
    }
