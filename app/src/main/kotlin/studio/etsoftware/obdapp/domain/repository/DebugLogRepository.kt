package studio.etsoftware.obdapp.domain.repository

import kotlinx.coroutines.flow.StateFlow
import studio.etsoftware.obdapp.domain.model.DebugLogEntry

interface DebugLogRepository {
    val logs: StateFlow<List<DebugLogEntry>>

    suspend fun clear()
}
