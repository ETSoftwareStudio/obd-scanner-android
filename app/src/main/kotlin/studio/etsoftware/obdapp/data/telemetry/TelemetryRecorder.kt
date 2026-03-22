package studio.etsoftware.obdapp.data.telemetry

import studio.etsoftware.obdapp.domain.model.CommandTelemetry
import studio.etsoftware.obdapp.domain.model.CycleTelemetry
import studio.etsoftware.obdapp.domain.model.MetricEmissionTelemetry
import studio.etsoftware.obdapp.domain.repository.TelemetryRepository
import studio.etsoftware.obdapp.data.logging.LogManager
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TelemetryRecorder
    @Inject
    constructor(
        private val telemetryRepository: TelemetryRepository,
        private val logManager: LogManager,
    ) {
        fun currentSessionId(): String = telemetryRepository.currentSessionId()

        fun nextCycleId(): Long = telemetryRepository.nextCycleId()

        suspend fun recordCommand(event: CommandTelemetry) {
            if (!telemetryRepository.isEnabled.value) return
            telemetryRepository.record(event)
            logManager.telemetry(formatCommand(event))
        }

        suspend fun recordCycle(event: CycleTelemetry) {
            if (!telemetryRepository.isEnabled.value) return
            telemetryRepository.record(event)
            logManager.telemetry(formatCycle(event))
        }

        suspend fun recordMetricEmission(event: MetricEmissionTelemetry) {
            if (!telemetryRepository.isEnabled.value) return
            telemetryRepository.record(event)
            logManager.telemetry(formatMetricEmission(event))
        }

        private fun formatCommand(event: CommandTelemetry): String =
            buildString {
                append("CMD")
                event.cycleId?.let { append(" cycle=$it") }
                append(" ctx=${event.context.name.lowercase()}")
                append(" pid=${event.rawPid}")
                append(" name=${event.commandName}")
                append(" dur=${event.durationMs}ms")
                append(" ok=${event.success}")
                event.errorType?.let { append(" err=$it") }
                event.errorMessage?.let { append(" msg=$it") }
                event.valuePreview?.let { append(" value=$it") }
            }

        private fun formatCycle(event: CycleTelemetry): String = "CYCLE id=${event.cycleId} dur=${event.durationMs}ms interval=${event.configuredIntervalMs}ms ok=${event.successCount} fail=${event.failureCount}"

        private fun formatMetricEmission(event: MetricEmissionTelemetry): String =
            buildString {
                append("EMIT")
                event.cycleId?.let { append(" cycle=$it") }
                append(" metric=${event.metricName}")
                append(" value=${event.value}")
            }
    }
