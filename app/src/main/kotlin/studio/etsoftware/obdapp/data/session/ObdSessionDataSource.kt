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

    suspend fun <T> withConnectedSession(block: suspend (ObdDeviceConnection) -> Result<T>): Result<T>

    @Deprecated("Use withConnectedSession instead to keep connection ownership inside the session layer")
    fun currentConnection(): ObdDeviceConnection?

    fun isTransportConnected(): Boolean

    @Deprecated("Use withConnectedSession instead to keep connection ownership inside the session layer")
    suspend fun <T> withConnectionAccess(block: suspend () -> T): T
}
