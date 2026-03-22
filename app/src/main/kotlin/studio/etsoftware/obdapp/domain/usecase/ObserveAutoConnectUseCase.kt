package studio.etsoftware.obdapp.domain.usecase

import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import studio.etsoftware.obdapp.domain.repository.AppSettingsRepository

class ObserveAutoConnectUseCase
    @Inject
    constructor(
        private val appSettingsRepository: AppSettingsRepository,
    ) {
        operator fun invoke(): Flow<Boolean> = appSettingsRepository.autoConnect
    }
