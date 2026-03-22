package studio.etsoftware.obdapp.domain.usecase

import javax.inject.Inject
import studio.etsoftware.obdapp.domain.repository.DiscoveryRepository

class StartDiscoveryUseCase
    @Inject
    constructor(
        private val discoveryRepository: DiscoveryRepository,
    ) {
        operator fun invoke(): Result<Unit> = discoveryRepository.startDiscovery()
    }
