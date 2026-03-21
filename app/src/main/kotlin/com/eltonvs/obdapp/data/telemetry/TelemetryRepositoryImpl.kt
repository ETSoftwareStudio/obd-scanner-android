package com.eltonvs.obdapp.data.telemetry

import com.eltonvs.obdapp.domain.model.TelemetryEvent
import com.eltonvs.obdapp.domain.repository.TelemetryRepository
import java.util.UUID
import java.util.concurrent.atomic.AtomicLong
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@Singleton
class TelemetryRepositoryImpl
    @Inject
    constructor(
        private val settingsDataSource: TelemetrySettingsDataSource,
    ) : TelemetryRepository {
        private val _events = MutableStateFlow<List<TelemetryEvent>>(emptyList())
        override val events: StateFlow<List<TelemetryEvent>> = _events.asStateFlow()

        private val _isEnabled = MutableStateFlow(false)
        override val isEnabled: StateFlow<Boolean> = _isEnabled.asStateFlow()

        private val sessionId: String = UUID.randomUUID().toString()
        private val cycleCounter = AtomicLong(0)
        private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

        init {
            scope.launch {
                settingsDataSource.observeEnabled().collectLatest { enabled ->
                    _isEnabled.value = enabled
                    if (!enabled) {
                        clear()
                    }
                }
            }
        }

        override fun currentSessionId(): String = sessionId

        override fun nextCycleId(): Long = cycleCounter.incrementAndGet()

        override suspend fun record(event: TelemetryEvent) {
            if (!_isEnabled.value) return
            _events.update { current ->
                (current + event).takeLast(MAX_EVENTS)
            }
        }

        override suspend fun clear() {
            _events.value = emptyList()
        }

        override suspend fun setEnabled(enabled: Boolean) {
            settingsDataSource.setEnabled(enabled)
        }

        companion object {
            private const val MAX_EVENTS = 1500
        }
    }
