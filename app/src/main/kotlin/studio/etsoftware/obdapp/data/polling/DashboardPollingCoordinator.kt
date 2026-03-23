package studio.etsoftware.obdapp.data.polling

import android.os.SystemClock
import com.github.eltonvs.obd.command.engine.MassAirFlowCommand
import com.github.eltonvs.obd.command.engine.RPMCommand
import com.github.eltonvs.obd.command.engine.SpeedCommand
import com.github.eltonvs.obd.command.engine.ThrottlePositionCommand
import com.github.eltonvs.obd.command.temperature.AirIntakeTemperatureCommand
import com.github.eltonvs.obd.command.temperature.EngineCoolantTemperatureCommand
import com.github.eltonvs.obd.command.fuel.FuelLevelCommand
import com.github.eltonvs.obd.connection.ObdDeviceConnection
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import studio.etsoftware.obdapp.data.session.ObdCommandExecutor
import studio.etsoftware.obdapp.data.session.ObdSessionDataSource
import studio.etsoftware.obdapp.data.telemetry.TelemetryRecorder
import studio.etsoftware.obdapp.domain.model.ConnectionState
import studio.etsoftware.obdapp.domain.model.CycleTelemetry
import studio.etsoftware.obdapp.domain.model.TelemetryContext
import studio.etsoftware.obdapp.data.logging.LogManager

@Singleton
class DashboardPollingCoordinator
    @Inject
    constructor(
        private val logManager: LogManager,
        private val telemetryRecorder: TelemetryRecorder,
        private val metricsStore: DashboardMetricsStore,
        private val commandExecutor: ObdCommandExecutor,
        private val sessionDataSource: ObdSessionDataSource,
    ) {
        private data class PollingCycleStats(
            val commandCount: Int,
            val successCount: Int,
            val failureCount: Int,
        )

        private val scope = CoroutineScope(Dispatchers.IO)
        private var pollingJob: Job? = null
        private var activePollingIntervalMs: Long? = null
        private var pendingPollingIntervalMs: Long? = null
        private val pollingConfigUpdates = Channel<Unit>(capacity = Channel.CONFLATED)
        private val pollingLifecycleMutex = Mutex()

        suspend fun startPolling(intervalMs: Long) {
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

        suspend fun stopPolling() {
            pollingLifecycleMutex.withLock {
                pollingJob?.cancelAndJoin()
                pollingJob = null
                activePollingIntervalMs = null
                pendingPollingIntervalMs = null
            }
        }

        suspend fun <T> runWithPollingPaused(
            reason: String,
            resumeLabel: String,
            onFailure: (Exception) -> Unit = {},
            block: suspend () -> Result<T>,
        ): Result<T> =
            runWhilePollingPaused(
                reason = reason,
                resumeLabel = resumeLabel,
                onFailure = onFailure,
                block = block,
            )

        @Deprecated("Use the overload that does not expose ObdDeviceConnection")
        suspend fun <T> runWithPollingPausedUsingConnection(
            reason: String,
            resumeLabel: String,
            onFailure: (Exception) -> Unit = {},
            block: suspend (ObdDeviceConnection) -> Result<T>,
        ): Result<T> =
            runWhilePollingPaused(
                reason = reason,
                resumeLabel = resumeLabel,
                onFailure = onFailure,
            ) {
                sessionDataSource.withConnectedSession(block)
            }

        private suspend fun <T> runWhilePollingPaused(
            reason: String,
            resumeLabel: String,
            onFailure: (Exception) -> Unit,
            block: suspend () -> Result<T>,
        ): Result<T> =
            pollingLifecycleMutex.withLock {
                val resumeInterval = (pendingPollingIntervalMs ?: activePollingIntervalMs).takeIf { pollingJob?.isActive == true }

                try {
                    if (resumeInterval != null) {
                        logManager.info("Pausing dashboard polling for $reason")
                        pollingJob?.cancelAndJoin()
                        pollingJob = null
                    }

                    block()
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    onFailure(e)
                    Result.failure(e)
                } finally {
                    if (resumeInterval != null) {
                        val canResumePolling =
                            sessionDataSource.withConnectedSession {
                                Result.success(Unit)
                            }.isSuccess &&
                                sessionDataSource.isTransportConnected() &&
                                sessionDataSource.connectionState.value is ConnectionState.Connected

                        if (canResumePolling) {
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

                    val connection = sessionDataSource.currentConnection()
                    if (connection == null || !sessionDataSource.isTransportConnected()) {
                        sessionDataSource.disconnect()
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
            if (delayMs <= 0) return

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

            return PollingCycleStats(commandCount, successCount, failureCount)
        }

        private suspend fun pollMetric(
            connection: ObdDeviceConnection,
            cycleId: Long,
            metricId: DashboardMetricId,
        ): Boolean =
            when (metricId) {
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
                    sessionDataSource.withConnectionAccess {
                        commandExecutor.execute(
                            context = TelemetryContext.DASHBOARD,
                            cycleId = cycleId,
                            rawPid = rawPid,
                            commandName = commandName,
                            block = read,
                            preview = { valueOf(it) },
                        )
                    }

                val value = valueOf(response)
                val unit = unitOf(response)
                val rawValue = commandExecutor.previewValue(rawValueOf(response)) ?: rawValueOf(response)
                logManager.response("$rawPid: $value (raw=$rawValue)")

                metricsStore.publish(cycleId, metricId, value, unit, minValue, maxValue)
                true
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                val errorMsg = e.message?.takeIf { it.isNotBlank() } ?: e::class.simpleName ?: "Unknown error"
                logManager.error("Read error for $rawPid: $errorMsg")
                false
            }
        }

        private fun wallClockMs(): Long = System.currentTimeMillis()

        private fun monotonicNowMs(): Long = SystemClock.elapsedRealtime()
    }
