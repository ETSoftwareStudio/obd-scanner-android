package studio.etsoftware.obdapp.domain.repository

import kotlinx.coroutines.flow.StateFlow
import studio.etsoftware.obdapp.domain.model.ConnectionState
import studio.etsoftware.obdapp.domain.model.DeviceInfo

interface ConnectionRepository {
    val connectionState: StateFlow<ConnectionState>

    suspend fun getPairedDevices(): List<DeviceInfo>

    suspend fun connect(device: DeviceInfo): Result<Unit>

    suspend fun disconnect()
}
