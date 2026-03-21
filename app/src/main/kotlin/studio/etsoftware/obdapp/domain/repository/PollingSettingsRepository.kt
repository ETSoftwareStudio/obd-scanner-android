package studio.etsoftware.obdapp.domain.repository

import kotlinx.coroutines.flow.Flow

interface PollingSettingsRepository {
    val pollingInterval: Flow<Long>

    suspend fun setPollingInterval(intervalMs: Long)
}
