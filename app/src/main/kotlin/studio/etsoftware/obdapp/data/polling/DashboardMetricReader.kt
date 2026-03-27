package studio.etsoftware.obdapp.data.polling

import com.github.eltonvs.obd.command.ObdCommand
import com.github.eltonvs.obd.command.ObdResponse
import com.github.eltonvs.obd.command.engine.MassAirFlowCommand
import com.github.eltonvs.obd.command.engine.RPMCommand
import com.github.eltonvs.obd.command.engine.SpeedCommand
import com.github.eltonvs.obd.command.engine.ThrottlePositionCommand
import com.github.eltonvs.obd.command.fuel.FuelLevelCommand
import com.github.eltonvs.obd.command.temperature.AirIntakeTemperatureCommand
import com.github.eltonvs.obd.command.temperature.EngineCoolantTemperatureCommand
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import studio.etsoftware.obdapp.data.logging.LogManager
import studio.etsoftware.obdapp.data.session.ObdSessionDataSource
import studio.etsoftware.obdapp.domain.model.TelemetryContext

internal data class DashboardReadStats(
    val commandCount: Int,
    val successCount: Int,
    val failureCount: Int,
)

@Singleton
class DashboardMetricReader
    @Inject
    constructor(
        private val logManager: LogManager,
        private val metricsStore: DashboardMetricsStore,
        private val sessionDataSource: ObdSessionDataSource,
    ) {
        internal suspend fun readDueMetrics(
            cycleId: Long,
            dueMetrics: List<DashboardMetricId>,
            onMetricProcessed: (DashboardMetricId) -> Unit,
        ): DashboardReadStats {
            var commandCount = 0
            var successCount = 0
            var failureCount = 0

            dueMetrics.forEach { metricId ->
                commandCount++
                val wasSuccessful = pollMetric(cycleId, metricId)
                onMetricProcessed(metricId)

                if (wasSuccessful) {
                    successCount++
                } else {
                    failureCount++
                }
            }

            return DashboardReadStats(
                commandCount = commandCount,
                successCount = successCount,
                failureCount = failureCount,
            )
        }

        private suspend fun pollMetric(
            cycleId: Long,
            metricId: DashboardMetricId,
        ): Boolean =
            when (metricId) {
                DashboardMetricId.SPEED ->
                    pollTypedMetric(
                        cycleId = cycleId,
                        metricId = metricId,
                        commandLabel = "010D (Speed)",
                        command = SpeedCommand(),
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
                        commandLabel = "010C (RPM)",
                        command = RPMCommand(),
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
                        commandLabel = "0111 (Throttle)",
                        command = ThrottlePositionCommand(),
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
                        commandLabel = "0110 (MAF)",
                        command = MassAirFlowCommand(),
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
                        commandLabel = "0105 (Coolant)",
                        command = EngineCoolantTemperatureCommand(),
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
                        commandLabel = "010F (Intake Air)",
                        command = AirIntakeTemperatureCommand(),
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
                        commandLabel = "012F (Fuel Level)",
                        command = FuelLevelCommand(),
                        valueOf = { it.value },
                        rawValueOf = { it.rawResponse.value },
                        unitOf = { it.unit },
                        minValue = 0f,
                        maxValue = 100f,
                    )
            }

        private suspend fun pollTypedMetric(
            cycleId: Long,
            metricId: DashboardMetricId,
            commandLabel: String,
            command: ObdCommand,
            valueOf: (ObdResponse) -> String,
            rawValueOf: (ObdResponse) -> String,
            unitOf: (ObdResponse) -> String,
            minValue: Float,
            maxValue: Float,
        ): Boolean {
            logManager.command(commandLabel)

            return try {
                val commandOutput =
                    sessionDataSource
                        .withConnectedSession { session ->
                            val response =
                                session.run(
                                    context = TelemetryContext.DASHBOARD,
                                    cycleId = cycleId,
                                    command = command,
                                    preview = valueOf,
                                )
                            val rawValue = rawValueOf(response)
                            Result.success(
                                Triple(
                                    response,
                                    rawValue,
                                    session.previewValue(rawValue) ?: rawValue,
                                ),
                            )
                        }.getOrThrow()
                val (response, _, previewValue) = commandOutput

                val value = valueOf(response)
                val unit = unitOf(response)
                logManager.response("${command.rawCommand.replace(" ", "")}: $value (raw=$previewValue)")

                metricsStore.publish(cycleId, metricId, value, unit, minValue, maxValue)
                true
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                val errorMsg = e.message?.takeIf { it.isNotBlank() } ?: e::class.simpleName ?: "Unknown error"
                logManager.error("Read error for ${command.rawCommand.replace(" ", "")}: $errorMsg")
                false
            }
        }
    }
