package studio.etsoftware.obdapp.data.repository

import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import studio.etsoftware.obdapp.data.diagnostics.DiagnosticsService
import studio.etsoftware.obdapp.data.polling.DashboardPollingCoordinator
import studio.etsoftware.obdapp.domain.model.DiagnosticInfo
import studio.etsoftware.obdapp.domain.repository.DiagnosticsRepository
import studio.etsoftware.obdapp.data.logging.LogManager

@Singleton
class DiagnosticsRepositoryImpl
    @Inject
    constructor(
        private val diagnosticsService: DiagnosticsService,
        private val pollingCoordinator: DashboardPollingCoordinator,
        private val logManager: LogManager,
    ) : DiagnosticsRepository {
        override suspend fun readDiagnosticInfo(): Result<DiagnosticInfo> =
            withContext(Dispatchers.IO) {
                pollingCoordinator.runWithPollingPaused(
                    reason = "diagnostics read",
                    resumeLabel = "diagnostics read",
                ) {
                    diagnosticsService.readDiagnosticInfo()
                }
            }

        override suspend fun clearTroubleCodes(): Result<Unit> =
            withContext(Dispatchers.IO) {
                pollingCoordinator.runWithPollingPaused(
                    reason = "trouble code clear",
                    resumeLabel = "trouble code clear",
                    onFailure = { error -> logManager.error("Failed to clear trouble codes: ${error.message}") },
                ) {
                    diagnosticsService.clearTroubleCodes()
                }
            }
    }
