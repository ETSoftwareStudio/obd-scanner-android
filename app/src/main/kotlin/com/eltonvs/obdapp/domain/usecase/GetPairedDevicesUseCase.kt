package com.eltonvs.obdapp.domain.usecase

import com.eltonvs.obdapp.domain.model.DeviceInfo
import com.eltonvs.obdapp.domain.repository.ObdRepository
import javax.inject.Inject

class GetPairedDevicesUseCase @Inject constructor(
    private val repository: ObdRepository
) {
    suspend operator fun invoke(): List<DeviceInfo> {
        return repository.getPairedDevices()
    }
}
