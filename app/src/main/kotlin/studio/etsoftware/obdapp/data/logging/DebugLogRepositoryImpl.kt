package studio.etsoftware.obdapp.data.logging

import javax.inject.Inject
import javax.inject.Singleton
import studio.etsoftware.obdapp.domain.model.DebugLogEntry
import studio.etsoftware.obdapp.domain.repository.DebugLogRepository
import studio.etsoftware.obdapp.util.LogManager
import kotlinx.coroutines.flow.StateFlow

@Singleton
class DebugLogRepositoryImpl
    @Inject
    constructor(
        private val logManager: LogManager,
    ) : DebugLogRepository {
        override val logs: StateFlow<List<DebugLogEntry>> = logManager.logs

        override suspend fun clear() {
            logManager.clear()
        }
    }
