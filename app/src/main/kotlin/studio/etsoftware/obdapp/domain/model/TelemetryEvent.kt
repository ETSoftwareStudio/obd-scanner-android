package studio.etsoftware.obdapp.domain.model

enum class TelemetryContext {
    INIT,
    DASHBOARD,
    DIAGNOSTICS,
    CLEAR_DTC,
}

sealed interface TelemetryEvent

data class CommandTelemetry(
    val sessionId: String,
    val cycleId: Long?,
    val context: TelemetryContext,
    val commandName: String,
    val rawPid: String,
    val startedAtMs: Long,
    val finishedAtMs: Long,
    val durationMs: Long,
    val success: Boolean,
    val errorType: String? = null,
    val errorMessage: String? = null,
    val valuePreview: String? = null,
) : TelemetryEvent

data class CycleTelemetry(
    val sessionId: String,
    val cycleId: Long,
    val startedAtMs: Long,
    val finishedAtMs: Long,
    val durationMs: Long,
    val configuredIntervalMs: Long,
    val commandCount: Int,
    val successCount: Int,
    val failureCount: Int,
) : TelemetryEvent

data class MetricEmissionTelemetry(
    val sessionId: String,
    val cycleId: Long?,
    val metricName: String,
    val emittedAtMs: Long,
    val value: String,
) : TelemetryEvent
