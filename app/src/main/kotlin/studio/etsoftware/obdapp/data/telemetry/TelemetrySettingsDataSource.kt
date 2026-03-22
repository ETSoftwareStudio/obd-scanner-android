package studio.etsoftware.obdapp.data.telemetry

import studio.etsoftware.obdapp.data.settings.PreferencesManager
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
