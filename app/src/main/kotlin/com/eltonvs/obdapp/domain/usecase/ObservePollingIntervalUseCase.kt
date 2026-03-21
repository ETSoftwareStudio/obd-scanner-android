package com.eltonvs.obdapp.domain.usecase

import com.eltonvs.obdapp.domain.repository.PollingSettingsRepository
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow

class ObservePollingIntervalUseCase
    @Inject
    constructor(
        private val pollingSettingsRepository: PollingSettingsRepository,
    ) {
        operator fun invoke(): Flow<Long> = pollingSettingsRepository.pollingInterval
    }
