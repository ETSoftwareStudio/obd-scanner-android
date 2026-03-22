package studio.etsoftware.obdapp.data.settings

import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import studio.etsoftware.obdapp.domain.model.DeviceInfo
import studio.etsoftware.obdapp.domain.model.DeviceType
import studio.etsoftware.obdapp.domain.repository.AppSettingsRepository
import studio.etsoftware.obdapp.util.PreferencesManager

@Singleton
class AppSettingsRepositoryImpl
    @Inject
    constructor(
        private val preferencesManager: PreferencesManager,
    ) : AppSettingsRepository {
        override val theme: Flow<String> = preferencesManager.theme
        override val autoConnect: Flow<Boolean> = preferencesManager.autoConnect
        override val wasConnected: Flow<Boolean> = preferencesManager.wasConnected
        override val lastDevice: Flow<DeviceInfo?> =
            combine(preferencesManager.lastDeviceAddress, preferencesManager.lastDeviceName) { address, name ->
                if (address == null || name == null) {
                    null
                } else {
                    DeviceInfo(
                        address = address,
                        name = name,
                        type = DeviceType.CLASSIC,
                    )
                }
            }

        override suspend fun setTheme(theme: String) {
            preferencesManager.setTheme(theme)
        }

        override suspend fun setAutoConnect(enabled: Boolean) {
            preferencesManager.setAutoConnect(enabled)
        }

        override suspend fun setWasConnected(connected: Boolean) {
            preferencesManager.setWasConnected(connected)
        }

        override suspend fun setLastDevice(device: DeviceInfo) {
            preferencesManager.setLastDevice(device.address, device.name)
        }
    }
