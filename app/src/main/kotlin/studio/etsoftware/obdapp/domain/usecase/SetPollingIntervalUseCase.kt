package studio.etsoftware.obdapp.domain.usecase

import studio.etsoftware.obdapp.domain.repository.PollingSettingsRepository
import javax.inject.Inject

class SetPollingIntervalUseCase
    @Inject
    constructor(
        private val pollingSettingsRepository: PollingSettingsRepository,
    ) {
        suspend operator fun invoke(intervalMs: Long) {
            pollingSettingsRepository.setPollingInterval(intervalMs)
        }
    }
