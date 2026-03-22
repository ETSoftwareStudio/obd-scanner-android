package studio.etsoftware.obdapp.data.repository

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.SystemClock
import androidx.core.content.ContextCompat
import studio.etsoftware.obdapp.data.connection.BluetoothDiscoveryManager
import studio.etsoftware.obdapp.data.connection.ObdTransport
import studio.etsoftware.obdapp.data.telemetry.TelemetryRecorder
import studio.etsoftware.obdapp.domain.model.CommandTelemetry
import studio.etsoftware.obdapp.domain.model.ConnectionState
import studio.etsoftware.obdapp.domain.model.CycleTelemetry
import studio.etsoftware.obdapp.domain.model.DeviceInfo
import studio.etsoftware.obdapp.domain.model.DeviceType
import studio.etsoftware.obdapp.domain.model.DiagnosticInfo
import studio.etsoftware.obdapp.domain.model.DiscoveryState
import studio.etsoftware.obdapp.domain.model.PairingState
import studio.etsoftware.obdapp.domain.model.TelemetryContext
import studio.etsoftware.obdapp.domain.model.TroubleCode
import studio.etsoftware.obdapp.domain.model.TroubleCodeType
import studio.etsoftware.obdapp.domain.model.VehicleMetric
import studio.etsoftware.obdapp.domain.repository.ObdRepository
import studio.etsoftware.obdapp.util.LogManager
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
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

@Singleton
class ObdRepositoryImpl
    @Inject
    constructor(
        @param:ApplicationContext private val appContext: Context,
        private val transport: ObdTransport,
        private val discoveryManager: BluetoothDiscoveryManager,
        private val logManager: LogManager,
        private val telemetryRecorder: TelemetryRecorder,
        private val dtcParser: DtcParser,
        private val metricsStore: DashboardMetricsStore,
    ) : ObdRepository {
        private data class PollingCycleStats(
            val commandCount: Int,
            val successCount: Int,
            val failureCount: Int,
        )

        private var obdConnection: ObdDeviceConnection? = null
        private val scope = CoroutineScope(Dispatchers.IO)
        private var pollingJob: Job? = null
        private var activePollingIntervalMs: Long? = null
        private var pendingPollingIntervalMs: Long? = null
        private val pollingConfigUpdates = Channel<Unit>(capacity = Channel.CONFLATED)
        private val pollingLifecycleMutex = Mutex()
        private val connectionAccessMutex = Mutex()

        private val _connectionState = MutableStateFlow<ConnectionState>(ConnectionState.Disconnected)
        override val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()
        override val discoveryState: StateFlow<DiscoveryState> = discoveryManager.discoveryState
        override val pairingState: StateFlow<PairingState> = discoveryManager.pairingState

        override val vehicleMetrics: Flow<VehicleMetric> = metricsStore.vehicleMetrics
        override val dashboardMetrics = metricsStore.dashboardMetrics

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
                withConnectionAccess {
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
                }

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
            stopPolling()
            withConnectionAccess {
                obdConnection = null
                transport.disconnect()
            }
            _connectionState.value = ConnectionState.Disconnected
            logManager.info("Disconnected")
        }

        override suspend fun readDiagnosticInfo(): Result<DiagnosticInfo> =
            withContext(Dispatchers.IO) {
                runExclusiveOperation(
                    reason = "diagnostics read",
                    resumeLabel = "diagnostics read",
                ) { connection ->
                    val vin =
                        traceCommand(
                            context = TelemetryContext.DIAGNOSTICS,
                            cycleId = null,
                            rawPid = "VIN",
                            commandName = "VINCommand",
                            block = { connection.run(VINCommand()) },
                            preview = { response -> response.value },
                        )

                    val troubleCodes =
                        traceCommand(
                            context = TelemetryContext.DIAGNOSTICS,
                            cycleId = null,
                            rawPid = "03",
                            commandName = "TroubleCodesCommand",
                            block = { connection.run(TroubleCodesCommand()) },
                            preview = { response -> response.value },
                        )

                    val codes = dtcParser.parse(troubleCodes.value)

                    Result.success(
                        DiagnosticInfo(
                            vin = vin.value,
                            troubleCodes =
                                codes.map { code ->
                                    TroubleCode(code, dtcParser.descriptionFor(code), TroubleCodeType.CURRENT)
                                },
                            milStatus = codes.isNotEmpty(),
                            dtcCount = codes.size,
                        ),
                    )
                }
            }

        override suspend fun clearTroubleCodes(): Result<Unit> =
            withContext(Dispatchers.IO) {
                runExclusiveOperation(
                    reason = "trouble code clear",
                    resumeLabel = "trouble code clear",
                    onFailure = { error -> logManager.error("Failed to clear trouble codes: ${error.message}") },
                ) { connection ->
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
                }
            }

        override suspend fun startPolling(intervalMs: Long) {
            pollingLifecycleMutex.withLock {
                if (pollingJob?.isActive == true) {
                    if (activePollingIntervalMs != intervalMs || pendingPollingIntervalMs != null) {
                        pendingPollingIntervalMs = intervalMs
                        pollingConfigUpdates.trySend(Unit)
                    }
                    return
                }

                activePollingIntervalMs = intervalMs
                pendingPollingIntervalMs = null
                pollingJob = createPollingJob(intervalMs)
            }
        }

        override suspend fun stopPolling() {
            pollingLifecycleMutex.withLock {
                pollingJob?.cancelAndJoin()
                pollingJob = null
                activePollingIntervalMs = null
                pendingPollingIntervalMs = null
            }
        }

        private suspend fun <T> runExclusiveOperation(
            reason: String,
            resumeLabel: String,
            onFailure: (Exception) -> Unit = {},
            block: suspend (ObdDeviceConnection) -> Result<T>,
        ): Result<T> =
            pollingLifecycleMutex.withLock {
                val resumeInterval = (pendingPollingIntervalMs ?: activePollingIntervalMs).takeIf { pollingJob?.isActive == true }

                try {
                    val result =
                        withConnectionAccess {
                            if (resumeInterval != null) {
                                logManager.info("Pausing dashboard polling for $reason")
                                pollingJob?.cancelAndJoin()
                                pollingJob = null
                            }

                            val connection = obdConnection ?: return@withConnectionAccess Result.failure(Exception("Not connected"))
                            block(connection)
                        }

                    result
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    onFailure(e)
                    Result.failure(e)
                } finally {
                    if (resumeInterval != null) {
                        if (obdConnection != null && transport.isConnected() && _connectionState.value is ConnectionState.Connected) {
                            logManager.info("Resuming dashboard polling after $resumeLabel")
                            activePollingIntervalMs = resumeInterval
                            pendingPollingIntervalMs = null
                            pollingJob = createPollingJob(resumeInterval)
                        } else {
                            activePollingIntervalMs = null
                            pendingPollingIntervalMs = null
                        }
                    }
                }
            }

        private fun createPollingJob(initialIntervalMs: Long): Job =
            scope.launch {
                var currentIntervalMs = initialIntervalMs
                var scheduler = DashboardPollingScheduler(currentIntervalMs, monotonicNowMs())

                while (isActive) {
                    applyPendingPollingInterval(currentIntervalMs)?.let { nextIntervalMs ->
                        currentIntervalMs = nextIntervalMs
                        scheduler = DashboardPollingScheduler(currentIntervalMs, monotonicNowMs())
                    }

                    val connection = obdConnection
                    if (connection == null || !transport.isConnected()) {
                        _connectionState.value = ConnectionState.Disconnected
                        break
                    }

                    val now = monotonicNowMs()
                    val dueMetrics = scheduler.dueMetrics(now)

                    if (dueMetrics.isEmpty()) {
                        waitForPollingUpdate(scheduler.delayUntilNextWork(now))
                        continue
                    }

                    val cycleId = telemetryRecorder.nextCycleId()
                    val startedAtWall = wallClockMs()
                    val startedAtMono = monotonicNowMs()
                    val stats = readMetrics(connection, cycleId, dueMetrics, scheduler)
                    val finishedAtWall = wallClockMs()
                    val finishedAtMono = monotonicNowMs()

                    telemetryRecorder.recordCycle(
                        CycleTelemetry(
                            sessionId = telemetryRecorder.currentSessionId(),
                            cycleId = cycleId,
                            startedAtMs = startedAtWall,
                            finishedAtMs = finishedAtWall,
                            durationMs = finishedAtMono - startedAtMono,
                            configuredIntervalMs = currentIntervalMs,
                            commandCount = stats.commandCount,
                            successCount = stats.successCount,
                            failureCount = stats.failureCount,
                        ),
                    )

                    applyPendingPollingInterval(currentIntervalMs)?.let { nextIntervalMs ->
                        currentIntervalMs = nextIntervalMs
                        scheduler = DashboardPollingScheduler(currentIntervalMs, monotonicNowMs())
                        continue
                    }

                    waitForPollingUpdate(scheduler.delayUntilNextWork(monotonicNowMs()))
                }
            }

        private suspend fun applyPendingPollingInterval(currentIntervalMs: Long): Long? =
            pollingLifecycleMutex.withLock {
                val nextIntervalMs = pendingPollingIntervalMs
                if (nextIntervalMs != null && nextIntervalMs != currentIntervalMs) {
                    activePollingIntervalMs = nextIntervalMs
                    pendingPollingIntervalMs = null
                    nextIntervalMs
                } else {
                    if (nextIntervalMs == currentIntervalMs) {
                        pendingPollingIntervalMs = null
                    }
                    null
                }
            }

        private suspend fun waitForPollingUpdate(delayMs: Long) {
            if (delayMs <= 0) {
                return
            }

            withTimeoutOrNull(delayMs) {
                pollingConfigUpdates.receive()
            }
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
                scheduler.markExecuted(metricId, monotonicNowMs())

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
                        rawValueOf = { it.rawResponse.value },
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
                        rawValueOf = { it.rawResponse.value },
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
                        rawValueOf = { it.rawResponse.value },
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
                        rawValueOf = { it.rawResponse.value },
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
                        rawValueOf = { it.rawResponse.value },
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
                        rawValueOf = { it.rawResponse.value },
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
                        rawValueOf = { it.rawResponse.value },
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
            rawValueOf: (T) -> String,
            unitOf: (T) -> String,
            minValue: Float,
            maxValue: Float,
        ): Boolean {
            logManager.command(commandLabel)

            return try {
                val response =
                    withConnectionAccess {
                        traceCommand(
                            context = TelemetryContext.DASHBOARD,
                            cycleId = cycleId,
                            rawPid = rawPid.substringBefore(" "),
                            commandName = commandName,
                            block = read,
                            preview = { valueOf(it) },
                        )
                    }

                val value = valueOf(response)
                val unit = unitOf(response)
                val rawValue = previewValue(rawValueOf(response)) ?: rawValueOf(response)
                logManager.response("${rawPid.substringBefore(" ")}: $value (raw=$rawValue)")

                metricsStore.publish(
                    cycleId = cycleId,
                    metricId = metricId,
                    value = value,
                    unit = unit,
                    minValue = minValue,
                    maxValue = maxValue,
                )
                true
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                val errorMsg = e.message?.takeIf { it.isNotBlank() } ?: e::class.simpleName ?: "Unknown error"
                logManager.error("Read error for ${rawPid.substringBefore(" ")}: $errorMsg")
                false
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
            val startedAtWall = wallClockMs()
            val startedAtMono = monotonicNowMs()

            try {
                val result = block()
                val finishedAtWall = wallClockMs()
                val finishedAtMono = monotonicNowMs()
                val valuePreview = previewValue(preview(result))

                telemetryRecorder.recordCommand(
                    CommandTelemetry(
                        sessionId = telemetryRecorder.currentSessionId(),
                        cycleId = cycleId,
                        context = context,
                        commandName = commandName,
                        rawPid = rawPid,
                        startedAtMs = startedAtWall,
                        finishedAtMs = finishedAtWall,
                        durationMs = finishedAtMono - startedAtMono,
                        success = true,
                        valuePreview = valuePreview,
                    ),
                )

                return result
            } catch (e: Exception) {
                val finishedAtWall = wallClockMs()
                val finishedAtMono = monotonicNowMs()
                val errorType = e::class.simpleName ?: "Exception"
                val errorMessage = previewValue(e.message)

                telemetryRecorder.recordCommand(
                    CommandTelemetry(
                        sessionId = telemetryRecorder.currentSessionId(),
                        cycleId = cycleId,
                        context = context,
                        commandName = commandName,
                        rawPid = rawPid,
                        startedAtMs = startedAtWall,
                        finishedAtMs = finishedAtWall,
                        durationMs = finishedAtMono - startedAtMono,
                        success = false,
                        errorType = errorType,
                        errorMessage = errorMessage,
                    ),
                )

                throw e
            }
        }

        private suspend fun <T> withConnectionAccess(block: suspend () -> T): T {
            return connectionAccessMutex.withLock {
                block()
            }
        }

        private fun wallClockMs(): Long = System.currentTimeMillis()

        private fun monotonicNowMs(): Long = SystemClock.elapsedRealtime()

        private fun previewValue(value: String?): String? {
            return value
                ?.replace('\n', ' ')
                ?.replace('\r', ' ')
                ?.trim()
                ?.take(40)
        }

        private fun hasBluetoothConnectPermission(): Boolean {
            return Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
                ContextCompat.checkSelfPermission(
                    appContext,
                    Manifest.permission.BLUETOOTH_CONNECT,
                ) == PackageManager.PERMISSION_GRANTED
        }
    }
