package studio.etsoftware.obdapp.domain.usecase

import javax.inject.Inject
import studio.etsoftware.obdapp.domain.repository.AppSettingsRepository

class SetThemeUseCase
    @Inject
    constructor(
        private val appSettingsRepository: AppSettingsRepository,
    ) {
        suspend operator fun invoke(theme: String) {
            appSettingsRepository.setTheme(theme)
        }
    }
