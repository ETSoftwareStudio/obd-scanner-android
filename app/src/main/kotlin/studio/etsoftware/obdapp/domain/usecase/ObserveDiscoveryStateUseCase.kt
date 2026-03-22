package studio.etsoftware.obdapp.domain.usecase

import javax.inject.Inject
import kotlinx.coroutines.flow.StateFlow
import studio.etsoftware.obdapp.domain.model.DiscoveryState
import studio.etsoftware.obdapp.domain.repository.DiscoveryRepository

class ObserveDiscoveryStateUseCase
    @Inject
    constructor(
        private val discoveryRepository: DiscoveryRepository,
    ) {
        operator fun invoke(): StateFlow<DiscoveryState> = discoveryRepository.discoveryState
    }
