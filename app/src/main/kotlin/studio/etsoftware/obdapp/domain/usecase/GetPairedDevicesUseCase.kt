package studio.etsoftware.obdapp.domain.usecase

import studio.etsoftware.obdapp.domain.model.DeviceInfo
import studio.etsoftware.obdapp.domain.repository.ObdRepository
import javax.inject.Inject

class GetPairedDevicesUseCase
    @Inject
    constructor(
        private val repository: ObdRepository,
    ) {
        suspend operator fun invoke(): List<DeviceInfo> {
            return repository.getPairedDevices()
        }
    }
