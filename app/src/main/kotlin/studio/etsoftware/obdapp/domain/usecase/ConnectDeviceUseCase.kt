package studio.etsoftware.obdapp.domain.usecase

import studio.etsoftware.obdapp.domain.model.DeviceInfo
import studio.etsoftware.obdapp.domain.repository.ConnectionRepository
import javax.inject.Inject

class ConnectDeviceUseCase
    @Inject
    constructor(
        private val connectionRepository: ConnectionRepository,
    ) {
        suspend operator fun invoke(device: DeviceInfo): Result<Unit> = connectionRepository.connect(device)
    }
