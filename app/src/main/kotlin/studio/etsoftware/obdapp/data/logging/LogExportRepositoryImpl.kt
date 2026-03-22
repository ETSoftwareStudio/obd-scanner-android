package studio.etsoftware.obdapp.data.logging

import android.net.Uri
import javax.inject.Inject
import javax.inject.Singleton
import studio.etsoftware.obdapp.domain.model.DebugLogEntry
import studio.etsoftware.obdapp.domain.model.TelemetryEvent
import studio.etsoftware.obdapp.domain.repository.LogExportRepository
import studio.etsoftware.obdapp.util.LogExportFormatter
import studio.etsoftware.obdapp.util.LogExporter

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
        ): String {
            return formatter.buildExportText(logs, telemetryEvents, exportedAtMillis)
        }

        override fun buildDefaultFileName(nowMillis: Long): String {
            return formatter.buildDefaultFileName(nowMillis)
        }

        override suspend fun export(
            destination: String,
            content: String,
        ): Result<Unit> {
            return exporter.export(Uri.parse(destination), content)
        }
    }
