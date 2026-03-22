package studio.etsoftware.obdapp.domain.usecase

import javax.inject.Inject
import studio.etsoftware.obdapp.domain.model.DebugLogEntry
import studio.etsoftware.obdapp.domain.model.TelemetryEvent
import studio.etsoftware.obdapp.domain.repository.LogExportRepository

class BuildLogExportTextUseCase
    @Inject
    constructor(
        private val logExportRepository: LogExportRepository,
    ) {
        operator fun invoke(
            logs: List<DebugLogEntry>,
            telemetryEvents: List<TelemetryEvent> = emptyList(),
            exportedAtMillis: Long = System.currentTimeMillis(),
        ): String {
            return logExportRepository.buildExportText(logs, telemetryEvents, exportedAtMillis)
        }
    }
