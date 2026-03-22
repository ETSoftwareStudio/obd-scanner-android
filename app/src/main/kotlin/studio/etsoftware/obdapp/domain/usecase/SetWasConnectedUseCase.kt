package studio.etsoftware.obdapp.domain.usecase

import javax.inject.Inject
import studio.etsoftware.obdapp.domain.repository.AppSettingsRepository

class SetWasConnectedUseCase
    @Inject
    constructor(
        private val appSettingsRepository: AppSettingsRepository,
    ) {
        suspend operator fun invoke(connected: Boolean) {
            appSettingsRepository.setWasConnected(connected)
        }
    }
