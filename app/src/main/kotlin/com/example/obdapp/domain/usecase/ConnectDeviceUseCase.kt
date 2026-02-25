package com.example.obdapp.domain.usecase

import com.example.obdapp.domain.model.DeviceInfo
import com.example.obdapp.domain.repository.ObdRepository
import javax.inject.Inject

class ConnectDeviceUseCase @Inject constructor(
    private val repository: ObdRepository
) {
    suspend operator fun invoke(device: DeviceInfo): Result<Unit> {
        return repository.connect(device)
    }
}
