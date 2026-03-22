package studio.etsoftware.obdapp.domain.usecase

import javax.inject.Inject
import studio.etsoftware.obdapp.domain.repository.AppSettingsRepository

class SetAutoConnectUseCase
    @Inject
    constructor(
        private val appSettingsRepository: AppSettingsRepository,
    ) {
        suspend operator fun invoke(enabled: Boolean) {
            appSettingsRepository.setAutoConnect(enabled)
        }
    }
