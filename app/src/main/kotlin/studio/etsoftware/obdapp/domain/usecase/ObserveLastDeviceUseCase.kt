package studio.etsoftware.obdapp.domain.usecase

import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import studio.etsoftware.obdapp.domain.model.DeviceInfo
import studio.etsoftware.obdapp.domain.repository.AppSettingsRepository

class ObserveLastDeviceUseCase
    @Inject
    constructor(
        private val appSettingsRepository: AppSettingsRepository,
    ) {
        operator fun invoke(): Flow<DeviceInfo?> = appSettingsRepository.lastDevice
    }
