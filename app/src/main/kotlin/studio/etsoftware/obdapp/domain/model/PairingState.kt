package studio.etsoftware.obdapp.domain.model

sealed class PairingState {
    data object Idle : PairingState()

    data class Pairing(
        val device: DeviceInfo,
    ) : PairingState()

    data class Paired(
        val device: DeviceInfo,
    ) : PairingState()

    data class Error(
        val message: String,
        val device: DeviceInfo? = null,
    ) : PairingState()
}
