package studio.etsoftware.obdapp.data.session

import kotlinx.coroutines.flow.StateFlow
import studio.etsoftware.obdapp.domain.model.ConnectionState
import studio.etsoftware.obdapp.domain.model.DeviceInfo

interface ObdSessionDataSource {
    val connectionState: StateFlow<ConnectionState>

    suspend fun getPairedDevices(): List<DeviceInfo>

    suspend fun connect(device: DeviceInfo): Result<Unit>

    suspend fun disconnect()

    suspend fun <T> withConnectedSession(block: suspend (ObdCommandSession) -> Result<T>): Result<T>
}
