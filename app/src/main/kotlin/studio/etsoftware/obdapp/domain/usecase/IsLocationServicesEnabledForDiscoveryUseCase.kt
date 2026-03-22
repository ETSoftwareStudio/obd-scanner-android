package studio.etsoftware.obdapp.domain.usecase

import javax.inject.Inject
import studio.etsoftware.obdapp.domain.repository.DiscoveryRepository

class IsLocationServicesEnabledForDiscoveryUseCase
    @Inject
    constructor(
        private val discoveryRepository: DiscoveryRepository,
    ) {
        operator fun invoke(): Boolean = discoveryRepository.isLocationServicesEnabledForDiscovery()
    }
