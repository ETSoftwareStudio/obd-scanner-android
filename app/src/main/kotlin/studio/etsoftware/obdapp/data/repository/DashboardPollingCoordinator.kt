package studio.etsoftware.obdapp.data.repository

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
import studio.etsoftware.obdapp.data.telemetry.TelemetryRecorder
import studio.etsoftware.obdapp.domain.model.ConnectionState
import studio.etsoftware.obdapp.domain.model.CycleTelemetry
import studio.etsoftware.obdapp.domain.model.TelemetryContext
import studio.etsoftware.obdapp.util.LogManager

@Singleton
class DashboardPollingCoordinator
    @Inject
    constructor(
        private val logManager: LogManager,
        private val telemetryRecorder: TelemetryRecorder,
        private val metricsStore: DashboardMetricsStore,
        private val commandExecutor: ObdCommandExecutor,
        private val sessionManager: ObdSessionManager,
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
            block: suspend (ObdDeviceConnection) -> Result<T>,
        ): Result<T> =
            pollingLifecycleMutex.withLock {
                val resumeInterval = (pendingPollingIntervalMs ?: activePollingIntervalMs).takeIf { pollingJob?.isActive == true }

                try {
                    val result =
                        sessionManager.withConnectionAccess {
                            if (resumeInterval != null) {
                                logManager.info("Pausing dashboard polling for $reason")
                                pollingJob?.cancelAndJoin()
                                pollingJob = null
                            }

                            val connection = sessionManager.currentConnection() ?: return@withConnectionAccess Result.failure(Exception("Not connected"))
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
                        if (sessionManager.currentConnection() != null &&
                            sessionManager.isTransportConnected() &&
                            sessionManager.connectionState.value is ConnectionState.Connected
                        ) {
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

                    val connection = sessionManager.currentConnection()
                    if (connection == null || !sessionManager.isTransportConnected()) {
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
        ): Boolean {
            return when (metricId) {
                DashboardMetricId.SPEED -> pollTypedMetric(cycleId, metricId, "010D", "010D (Speed)", "SpeedCommand", { connection.run(SpeedCommand()) }, { it.value }, { it.rawResponse.value }, { it.unit }, 0f, 200f)
                DashboardMetricId.RPM -> pollTypedMetric(cycleId, metricId, "010C", "010C (RPM)", "RPMCommand", { connection.run(RPMCommand()) }, { it.value }, { it.rawResponse.value }, { it.unit }, 0f, 8000f)
                DashboardMetricId.THROTTLE -> pollTypedMetric(cycleId, metricId, "0111", "0111 (Throttle)", "ThrottlePositionCommand", { connection.run(ThrottlePositionCommand()) }, { it.value }, { it.rawResponse.value }, { it.unit }, 0f, 100f)
                DashboardMetricId.MAF -> pollTypedMetric(cycleId, metricId, "0110", "0110 (MAF)", "MassAirFlowCommand", { connection.run(MassAirFlowCommand()) }, { it.value }, { it.rawResponse.value }, { it.unit }, 0f, 655.35f)
                DashboardMetricId.COOLANT -> pollTypedMetric(cycleId, metricId, "0105", "0105 (Coolant)", "EngineCoolantTemperatureCommand", { connection.run(EngineCoolantTemperatureCommand()) }, { it.value }, { it.rawResponse.value }, { it.unit }, -40f, 215f)
                DashboardMetricId.INTAKE -> pollTypedMetric(cycleId, metricId, "010F", "010F (Intake Air)", "AirIntakeTemperatureCommand", { connection.run(AirIntakeTemperatureCommand()) }, { it.value }, { it.rawResponse.value }, { it.unit }, -40f, 215f)
                DashboardMetricId.FUEL -> pollTypedMetric(cycleId, metricId, "012F", "012F (Fuel Level)", "FuelLevelCommand", { connection.run(FuelLevelCommand()) }, { it.value }, { it.rawResponse.value }, { it.unit }, 0f, 100f)
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
                    sessionManager.withConnectionAccess {
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
