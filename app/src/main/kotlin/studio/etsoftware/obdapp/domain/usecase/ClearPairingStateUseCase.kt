package studio.etsoftware.obdapp.domain.usecase

import javax.inject.Inject
import studio.etsoftware.obdapp.domain.repository.DiscoveryRepository

class ClearPairingStateUseCase
    @Inject
    constructor(
        private val discoveryRepository: DiscoveryRepository,
    ) {
        operator fun invoke() = discoveryRepository.clearPairingState()
    }
