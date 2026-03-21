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
import com.eltonvs.obdapp.data.telemetry.TelemetryRecorder
import com.eltonvs.obdapp.domain.model.CommandTelemetry
import com.eltonvs.obdapp.domain.model.ConnectionState
import com.eltonvs.obdapp.domain.model.CycleTelemetry
import com.eltonvs.obdapp.domain.model.DashboardMetricsSnapshot
import com.eltonvs.obdapp.domain.model.DeviceInfo
import com.eltonvs.obdapp.domain.model.DeviceType
import com.eltonvs.obdapp.domain.model.DiagnosticInfo
import com.eltonvs.obdapp.domain.model.DiscoveryState
import com.eltonvs.obdapp.domain.model.MetricEmissionTelemetry
import com.eltonvs.obdapp.domain.model.PairingState
import com.eltonvs.obdapp.domain.model.TelemetryContext
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
        private val telemetryRecorder: TelemetryRecorder,
    ) : ObdRepository {
        private data class PollingCycleStats(
            val commandCount: Int,
            val successCount: Int,
            val failureCount: Int,
        )

        private var obdConnection: ObdDeviceConnection? = null
        private val scope = CoroutineScope(Dispatchers.IO)
        private var pollingJob: Job? = null

        private val _connectionState = MutableStateFlow<ConnectionState>(ConnectionState.Disconnected)
        override val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()
        override val discoveryState: StateFlow<DiscoveryState> = discoveryManager.discoveryState
        override val pairingState: StateFlow<PairingState> = discoveryManager.pairingState

        private val _metricsFlow = MutableSharedFlow<VehicleMetric>(replay = 1, extraBufferCapacity = 64)
        private val _dashboardMetrics = MutableStateFlow(DashboardMetricsSnapshot())
        private val dtcRegex = Regex("[PCBU][0-3][0-9A-F]{3}")

        override val vehicleMetrics: Flow<VehicleMetric> = _metricsFlow.asSharedFlow()
        override val dashboardMetrics: StateFlow<DashboardMetricsSnapshot> = _dashboardMetrics.asStateFlow()

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
                traceCommand(
                    context = TelemetryContext.INIT,
                    cycleId = null,
                    rawPid = "ATZ",
                    commandName = "ResetAdapterCommand",
                    block = { obdConnection?.run(ResetAdapterCommand()) ?: error("No OBD connection") },
                    preview = { it.value },
                )

                logManager.command("ATE0 (Echo off)")
                traceCommand(
                    context = TelemetryContext.INIT,
                    cycleId = null,
                    rawPid = "ATE0",
                    commandName = "SetEchoCommand",
                    block = { obdConnection?.run(SetEchoCommand(Switcher.OFF)) ?: error("No OBD connection") },
                    preview = { it.value },
                )

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
                    val vin =
                        traceCommand(
                            context = TelemetryContext.DIAGNOSTICS,
                            cycleId = null,
                            rawPid = "VIN",
                            commandName = "VINCommand",
                            block = { connection.run(VINCommand()) },
                            preview = { it.value },
                        )

                    val troubleCodes =
                        traceCommand(
                            context = TelemetryContext.DIAGNOSTICS,
                            cycleId = null,
                            rawPid = "03",
                            commandName = "TroubleCodesCommand",
                            block = { connection.run(TroubleCodesCommand()) },
                            preview = { it.value },
                        )

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
                    traceCommand(
                        context = TelemetryContext.CLEAR_DTC,
                        cycleId = null,
                        rawPid = "04",
                        commandName = "ResetTroubleCodesCommand",
                        block = { connection.run(ResetTroubleCodesCommand()) },
                    )
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
                    val scheduler = DashboardPollingScheduler(intervalMs, nowMs())

                    while (isActive) {
                        val connection = obdConnection
                        if (connection == null || !transport.isConnected()) {
                            _connectionState.value = ConnectionState.Disconnected
                            break
                        }

                        val now = nowMs()
                        val dueMetrics = scheduler.dueMetrics(now)

                        if (dueMetrics.isEmpty()) {
                            delay(scheduler.delayUntilNextWork(now))
                            continue
                        }

                        val cycleId = telemetryRecorder.nextCycleId()
                        val startedAt = now
                        val stats = readMetrics(connection, cycleId, dueMetrics, scheduler)
                        val finishedAt = nowMs()

                        telemetryRecorder.recordCycle(
                            CycleTelemetry(
                                sessionId = telemetryRecorder.currentSessionId(),
                                cycleId = cycleId,
                                startedAtMs = startedAt,
                                finishedAtMs = finishedAt,
                                durationMs = finishedAt - startedAt,
                                configuredIntervalMs = intervalMs,
                                commandCount = stats.commandCount,
                                successCount = stats.successCount,
                                failureCount = stats.failureCount,
                            ),
                        )

                        val delayMs = scheduler.delayUntilNextWork(nowMs())
                        if (delayMs > 0) {
                            delay(delayMs)
                        }
                    }
                }
        }

        override suspend fun stopPolling() {
            pollingJob?.cancel()
            pollingJob = null
        }

        private suspend fun readMetrics(
            connection: ObdDeviceConnection,
            cycleId: Long,
            dueMetrics: List<DashboardMetricId>,
            scheduler: DashboardPollingScheduler,
        ): PollingCycleStats {
            var commandCount = 0
            var successCount = 0
            var failureCount = 0

            dueMetrics.forEach { metricId ->
                commandCount++
                val wasSuccessful = pollMetric(connection, cycleId, metricId)
                scheduler.markExecuted(metricId, nowMs())

                if (wasSuccessful) {
                    successCount++
                } else {
                    failureCount++
                }
            }

            return PollingCycleStats(
                commandCount = commandCount,
                successCount = successCount,
                failureCount = failureCount,
            )
        }

        private suspend fun pollMetric(
            connection: ObdDeviceConnection,
            cycleId: Long,
            metricId: DashboardMetricId,
        ): Boolean {
            return when (metricId) {
                DashboardMetricId.SPEED ->
                    pollTypedMetric(
                        cycleId = cycleId,
                        metricId = metricId,
                        rawPid = "010D",
                        commandLabel = "010D (Speed)",
                        commandName = "SpeedCommand",
                        read = { connection.run(SpeedCommand()) },
                        valueOf = { it.value },
                        unitOf = { it.unit },
                        minValue = 0f,
                        maxValue = 200f,
                    )
                DashboardMetricId.RPM ->
                    pollTypedMetric(
                        cycleId = cycleId,
                        metricId = metricId,
                        rawPid = "010C",
                        commandLabel = "010C (RPM)",
                        commandName = "RPMCommand",
                        read = { connection.run(RPMCommand()) },
                        valueOf = { it.value },
                        unitOf = { it.unit },
                        minValue = 0f,
                        maxValue = 8000f,
                    )
                DashboardMetricId.THROTTLE ->
                    pollTypedMetric(
                        cycleId = cycleId,
                        metricId = metricId,
                        rawPid = "0111",
                        commandLabel = "0111 (Throttle)",
                        commandName = "ThrottlePositionCommand",
                        read = { connection.run(ThrottlePositionCommand()) },
                        valueOf = { it.value },
                        unitOf = { it.unit },
                        minValue = 0f,
                        maxValue = 100f,
                    )
                DashboardMetricId.MAF ->
                    pollTypedMetric(
                        cycleId = cycleId,
                        metricId = metricId,
                        rawPid = "0110",
                        commandLabel = "0110 (MAF)",
                        commandName = "MassAirFlowCommand",
                        read = { connection.run(MassAirFlowCommand()) },
                        valueOf = { it.value },
                        unitOf = { it.unit },
                        minValue = 0f,
                        maxValue = 655.35f,
                    )
                DashboardMetricId.COOLANT ->
                    pollTypedMetric(
                        cycleId = cycleId,
                        metricId = metricId,
                        rawPid = "0105",
                        commandLabel = "0105 (Coolant)",
                        commandName = "EngineCoolantTemperatureCommand",
                        read = { connection.run(EngineCoolantTemperatureCommand()) },
                        valueOf = { it.value },
                        unitOf = { it.unit },
                        minValue = -40f,
                        maxValue = 215f,
                    )
                DashboardMetricId.INTAKE ->
                    pollTypedMetric(
                        cycleId = cycleId,
                        metricId = metricId,
                        rawPid = "010F",
                        commandLabel = "010F (Intake Air)",
                        commandName = "AirIntakeTemperatureCommand",
                        read = { connection.run(AirIntakeTemperatureCommand()) },
                        valueOf = { it.value },
                        unitOf = { it.unit },
                        minValue = -40f,
                        maxValue = 215f,
                    )
                DashboardMetricId.FUEL ->
                    pollTypedMetric(
                        cycleId = cycleId,
                        metricId = metricId,
                        rawPid = "012F",
                        commandLabel = "012F (Fuel Level)",
                        commandName = "FuelLevelCommand",
                        read = { connection.run(FuelLevelCommand()) },
                        valueOf = { it.value },
                        unitOf = { it.unit },
                        minValue = 0f,
                        maxValue = 100f,
                    )
            }
        }

        private suspend fun <T> pollTypedMetric(
            cycleId: Long,
            metricId: DashboardMetricId,
            rawPid: String,
            commandLabel: String,
            commandName: String,
            read: suspend () -> T,
            valueOf: (T) -> String,
            unitOf: (T) -> String,
            minValue: Float,
            maxValue: Float,
        ): Boolean {
            logManager.command(commandLabel)

            return try {
                val response =
                    traceCommand(
                        context = TelemetryContext.DASHBOARD,
                        cycleId = cycleId,
                        rawPid = rawPid.substringBefore(" "),
                        commandName = commandName,
                        block = read,
                        preview = { valueOf(it) },
                    )

                val value = valueOf(response)
                val unit = unitOf(response)
                logManager.response("${rawPid.substringBefore(" ")}: $value")

                publishMetric(
                    cycleId = cycleId,
                    metricId = metricId,
                    value = value,
                    unit = unit,
                    minValue = minValue,
                    maxValue = maxValue,
                )
                true
            } catch (e: Exception) {
                val errorMsg = e.message?.takeIf { it.isNotBlank() } ?: e::class.simpleName ?: "Unknown error"
                logManager.error("Read error for ${rawPid.substringBefore(" ")}: $errorMsg")
                false
            }
        }

        private suspend fun publishMetric(
            cycleId: Long,
            metricId: DashboardMetricId,
            value: String,
            unit: String,
            minValue: Float,
            maxValue: Float,
        ) {
            val metricName = metricDisplayName(metricId)
            _metricsFlow.emit(
                VehicleMetric(
                    name = metricName,
                    value = value,
                    unit = unit,
                    minValue = minValue,
                    maxValue = maxValue,
                ),
            )
            _dashboardMetrics.value = updateDashboardSnapshot(metricId, value)
            recordMetricEmission(cycleId, metricName, value)
        }

        private fun updateDashboardSnapshot(
            metricId: DashboardMetricId,
            value: String,
        ): DashboardMetricsSnapshot {
            return when (metricId) {
                DashboardMetricId.SPEED -> _dashboardMetrics.value.copy(speed = value)
                DashboardMetricId.RPM -> _dashboardMetrics.value.copy(rpm = value)
                DashboardMetricId.THROTTLE -> _dashboardMetrics.value.copy(throttle = value)
                DashboardMetricId.MAF -> _dashboardMetrics.value.copy(maf = value)
                DashboardMetricId.COOLANT -> _dashboardMetrics.value.copy(coolantTemp = value)
                DashboardMetricId.INTAKE -> _dashboardMetrics.value.copy(intakeTemp = value)
                DashboardMetricId.FUEL -> _dashboardMetrics.value.copy(fuel = value)
            }
        }

        private fun metricDisplayName(metricId: DashboardMetricId): String {
            return when (metricId) {
                DashboardMetricId.SPEED -> "Speed"
                DashboardMetricId.RPM -> "RPM"
                DashboardMetricId.THROTTLE -> "Throttle"
                DashboardMetricId.MAF -> "MAF"
                DashboardMetricId.COOLANT -> "Coolant"
                DashboardMetricId.INTAKE -> "Intake"
                DashboardMetricId.FUEL -> "Fuel"
            }
        }

        private suspend fun <T> traceCommand(
            context: TelemetryContext,
            cycleId: Long?,
            rawPid: String,
            commandName: String,
            block: suspend () -> T,
            preview: (T) -> String? = { null },
        ): T {
            val startedAt = nowMs()

            try {
                val result = block()
                val finishedAt = nowMs()
                val durationMs = finishedAt - startedAt
                val valuePreview = previewValue(preview(result))

                telemetryRecorder.recordCommand(
                    CommandTelemetry(
                        sessionId = telemetryRecorder.currentSessionId(),
                        cycleId = cycleId,
                        context = context,
                        commandName = commandName,
                        rawPid = rawPid,
                        startedAtMs = startedAt,
                        finishedAtMs = finishedAt,
                        durationMs = durationMs,
                        success = true,
                        valuePreview = valuePreview,
                    ),
                )

                return result
            } catch (e: Exception) {
                val finishedAt = nowMs()
                val durationMs = finishedAt - startedAt
                val errorType = e::class.simpleName ?: "Exception"
                val errorMessage = previewValue(e.message)

                telemetryRecorder.recordCommand(
                    CommandTelemetry(
                        sessionId = telemetryRecorder.currentSessionId(),
                        cycleId = cycleId,
                        context = context,
                        commandName = commandName,
                        rawPid = rawPid,
                        startedAtMs = startedAt,
                        finishedAtMs = finishedAt,
                        durationMs = durationMs,
                        success = false,
                        errorType = errorType,
                        errorMessage = errorMessage,
                    ),
                )

                throw e
            }
        }

        private suspend fun recordMetricEmission(
            cycleId: Long?,
            metricName: String,
            value: String,
        ) {
            telemetryRecorder.recordMetricEmission(
                MetricEmissionTelemetry(
                    sessionId = telemetryRecorder.currentSessionId(),
                    cycleId = cycleId,
                    metricName = metricName,
                    emittedAtMs = nowMs(),
                    value = previewValue(value) ?: value,
                ),
            )
        }

        private fun nowMs(): Long = System.currentTimeMillis()

        private fun previewValue(value: String?): String? {
            return value
                ?.replace('\n', ' ')
                ?.replace('\r', ' ')
                ?.trim()
                ?.take(40)
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
