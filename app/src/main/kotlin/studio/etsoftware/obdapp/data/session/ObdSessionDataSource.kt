package studio.etsoftware.obdapp.data.session

import com.github.eltonvs.obd.connection.ObdDeviceConnection
import kotlinx.coroutines.flow.StateFlow
import studio.etsoftware.obdapp.domain.model.ConnectionState
import studio.etsoftware.obdapp.domain.model.DeviceInfo

interface ObdSessionDataSource {
    val connectionState: StateFlow<ConnectionState>

    suspend fun getPairedDevices(): List<DeviceInfo>

    suspend fun connect(device: DeviceInfo): Result<Unit>

    suspend fun disconnect()

    fun currentConnection(): ObdDeviceConnection?

    fun isTransportConnected(): Boolean

    suspend fun <T> withConnectionAccess(block: suspend () -> T): T
}
