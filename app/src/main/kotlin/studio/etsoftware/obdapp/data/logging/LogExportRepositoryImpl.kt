package studio.etsoftware.obdapp.data.logging

import androidx.core.net.toUri
import javax.inject.Inject
import javax.inject.Singleton
import studio.etsoftware.obdapp.domain.model.DebugLogEntry
import studio.etsoftware.obdapp.domain.model.TelemetryEvent
import studio.etsoftware.obdapp.domain.repository.LogExportRepository

@Singleton
class LogExportRepositoryImpl
    @Inject
    constructor(
        private val formatter: LogExportFormatter,
        private val exporter: LogExporter,
    ) : LogExportRepository {
        override fun buildExportText(
            logs: List<DebugLogEntry>,
            telemetryEvents: List<TelemetryEvent>,
            exportedAtMillis: Long,
        ): String = formatter.buildExportText(logs, telemetryEvents, exportedAtMillis)

        override fun buildDefaultFileName(nowMillis: Long): String = formatter.buildDefaultFileName(nowMillis)

        override suspend fun export(
            destination: String,
            content: String,
        ): Result<Unit> = exporter.export(destination.toUri(), content)
    }
