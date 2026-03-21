package studio.etsoftware.obdapp.domain.model

sealed class DiscoveryState {
    data object Idle : DiscoveryState()

    data object Starting : DiscoveryState()

    data class Discovering(
        val devices: List<DeviceInfo>,
    ) : DiscoveryState()

    data class Finished(
        val devices: List<DeviceInfo>,
    ) : DiscoveryState()

    data class Error(
        val message: String,
        val devices: List<DeviceInfo> = emptyList(),
    ) : DiscoveryState()
}
