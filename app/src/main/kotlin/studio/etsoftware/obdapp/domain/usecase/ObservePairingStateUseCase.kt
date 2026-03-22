package studio.etsoftware.obdapp.domain.usecase

import javax.inject.Inject
import kotlinx.coroutines.flow.StateFlow
import studio.etsoftware.obdapp.domain.model.PairingState
import studio.etsoftware.obdapp.domain.repository.DiscoveryRepository

class ObservePairingStateUseCase
    @Inject
    constructor(
        private val discoveryRepository: DiscoveryRepository,
    ) {
        operator fun invoke(): StateFlow<PairingState> = discoveryRepository.pairingState
    }
