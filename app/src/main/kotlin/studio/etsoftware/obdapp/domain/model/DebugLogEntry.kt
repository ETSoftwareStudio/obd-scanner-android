package studio.etsoftware.obdapp.domain.model

data class DebugLogEntry(
    val timestamp: String,
    val type: DebugLogType,
    val message: String,
)

enum class DebugLogType {
    INFO,
    COMMAND,
    RESPONSE,
    ERROR,
    SUCCESS,
    TELEMETRY,
}
