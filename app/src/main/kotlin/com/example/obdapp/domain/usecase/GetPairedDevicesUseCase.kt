package com.example.obdapp.domain.usecase

import com.example.obdapp.domain.model.DeviceInfo
import com.example.obdapp.domain.repository.ObdRepository
import javax.inject.Inject

class GetPairedDevicesUseCase @Inject constructor(
    private val repository: ObdRepository
) {
    suspend operator fun invoke(): List<DeviceInfo> {
        return repository.getPairedDevices()
    }
}
