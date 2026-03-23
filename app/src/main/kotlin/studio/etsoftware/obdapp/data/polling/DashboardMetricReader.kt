package studio.etsoftware.obdapp.data.polling

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
import studio.etsoftware.obdapp.data.session.ObdCommandExecutor
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
        private val commandExecutor: ObdCommandExecutor,
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
                        rawPid = "010D",
                        commandLabel = "010D (Speed)",
                        commandName = "SpeedCommand",
                        read = { connection -> connection.run(SpeedCommand()) },
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
                        read = { connection -> connection.run(RPMCommand()) },
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
                        read = { connection -> connection.run(ThrottlePositionCommand()) },
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
                        read = { connection -> connection.run(MassAirFlowCommand()) },
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
                        read = { connection -> connection.run(EngineCoolantTemperatureCommand()) },
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
                        read = { connection -> connection.run(AirIntakeTemperatureCommand()) },
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
                        read = { connection -> connection.run(FuelLevelCommand()) },
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
            read: suspend (com.github.eltonvs.obd.connection.ObdDeviceConnection) -> T,
            valueOf: (T) -> String,
            rawValueOf: (T) -> String,
            unitOf: (T) -> String,
            minValue: Float,
            maxValue: Float,
        ): Boolean {
            logManager.command(commandLabel)

            return try {
                val responseResult =
                    sessionDataSource.withConnectedSession { connection ->
                        Result.success(
                            commandExecutor.execute(
                                context = TelemetryContext.DASHBOARD,
                                cycleId = cycleId,
                                rawPid = rawPid,
                                commandName = commandName,
                                block = { read(connection) },
                                preview = { valueOf(it) },
                            ),
                        )
                    }
                val response = responseResult.getOrThrow()

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
    }
