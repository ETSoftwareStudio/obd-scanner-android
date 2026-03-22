package studio.etsoftware.obdapp.domain.repository

import kotlinx.coroutines.flow.Flow
import studio.etsoftware.obdapp.domain.model.DeviceInfo

interface AppSettingsRepository {
    val theme: Flow<String>
    val autoConnect: Flow<Boolean>
    val wasConnected: Flow<Boolean>
    val lastDevice: Flow<DeviceInfo?>

    suspend fun setTheme(theme: String)

    suspend fun setAutoConnect(enabled: Boolean)

    suspend fun setWasConnected(connected: Boolean)

    suspend fun setLastDevice(device: DeviceInfo)
}
