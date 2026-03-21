package studio.etsoftware.obdapp.domain.repository

import studio.etsoftware.obdapp.domain.model.TelemetryEvent
import kotlinx.coroutines.flow.StateFlow

interface TelemetryRepository {
    val isEnabled: StateFlow<Boolean>
    val events: StateFlow<List<TelemetryEvent>>

    fun currentSessionId(): String

    fun nextCycleId(): Long

    suspend fun record(event: TelemetryEvent)

    suspend fun clear()

    suspend fun setEnabled(enabled: Boolean)
}
