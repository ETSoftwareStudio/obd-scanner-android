package studio.etsoftware.obdapp.domain.usecase

import studio.etsoftware.obdapp.domain.model.DeviceInfo
import studio.etsoftware.obdapp.domain.repository.ConnectionRepository
import javax.inject.Inject

class GetPairedDevicesUseCase
    @Inject
    constructor(
        private val connectionRepository: ConnectionRepository,
    ) {
        suspend operator fun invoke(): List<DeviceInfo> {
            return connectionRepository.getPairedDevices()
        }
    }
