package com.eltonvs.obdapp.data.settings

import com.eltonvs.obdapp.domain.repository.PollingSettingsRepository
import com.eltonvs.obdapp.util.PreferencesManager
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
