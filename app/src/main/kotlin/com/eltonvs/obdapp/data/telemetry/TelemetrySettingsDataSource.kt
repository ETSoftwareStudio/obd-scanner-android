package com.eltonvs.obdapp.data.telemetry

import com.eltonvs.obdapp.util.PreferencesManager
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow

@Singleton
class TelemetrySettingsDataSource
    @Inject
    constructor(
        private val preferencesManager: PreferencesManager,
    ) {
        fun observeEnabled(): Flow<Boolean> = preferencesManager.telemetryEnabled

        suspend fun setEnabled(enabled: Boolean) {
            preferencesManager.setTelemetryEnabled(enabled)
        }
    }
