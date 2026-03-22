package studio.etsoftware.obdapp.data.settings

import studio.etsoftware.obdapp.domain.repository.PollingSettingsRepository
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow

@Singleton
class PollingSettingsRepositoryImpl
    @Inject
    constructor(
        private val preferencesManager: PreferencesManager,
    ) : PollingSettingsRepository {
        override val pollingInterval: Flow<Long> = preferencesManager.pollingInterval

        override suspend fun setPollingInterval(intervalMs: Long) {
            preferencesManager.setPollingInterval(intervalMs)
        }
    }
