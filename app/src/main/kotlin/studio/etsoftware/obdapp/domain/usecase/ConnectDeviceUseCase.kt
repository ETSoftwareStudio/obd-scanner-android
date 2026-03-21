package studio.etsoftware.obdapp.domain.usecase

import studio.etsoftware.obdapp.domain.model.DeviceInfo
import studio.etsoftware.obdapp.domain.repository.ObdRepository
import javax.inject.Inject

class ConnectDeviceUseCase
    @Inject
    constructor(
        private val repository: ObdRepository,
    ) {
        suspend operator fun invoke(device: DeviceInfo): Result<Unit> {
            return repository.connect(device)
        }
    }
