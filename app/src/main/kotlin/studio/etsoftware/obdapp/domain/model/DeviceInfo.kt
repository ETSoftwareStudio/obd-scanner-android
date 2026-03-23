package studio.etsoftware.obdapp.domain.model

data class DeviceInfo(
    val address: String,
    val name: String,
    val type: DeviceType,
)

enum class DeviceType {
    CLASSIC,
    BLE,
    UNKNOWN,
}

sealed class ConnectionState {
    data object Disconnected : ConnectionState()

    data object Connecting : ConnectionState()

    data object Connected : ConnectionState()

    data class Error(
        val message: String,
    ) : ConnectionState()
}
