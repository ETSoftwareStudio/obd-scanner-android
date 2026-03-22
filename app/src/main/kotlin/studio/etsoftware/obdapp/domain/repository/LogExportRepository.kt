package studio.etsoftware.obdapp.domain.repository

import studio.etsoftware.obdapp.domain.model.DebugLogEntry
import studio.etsoftware.obdapp.domain.model.TelemetryEvent

interface LogExportRepository {
    fun buildExportText(
        logs: List<DebugLogEntry>,
        telemetryEvents: List<TelemetryEvent> = emptyList(),
        exportedAtMillis: Long = System.currentTimeMillis(),
    ): String

    fun buildDefaultFileName(nowMillis: Long = System.currentTimeMillis()): String

    suspend fun export(
        destination: String,
        content: String,
    ): Result<Unit>
}
