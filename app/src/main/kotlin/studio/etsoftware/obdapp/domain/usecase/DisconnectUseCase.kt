package studio.etsoftware.obdapp.domain.usecase

import studio.etsoftware.obdapp.domain.repository.ConnectionRepository
import javax.inject.Inject

class DisconnectUseCase
    @Inject
    constructor(
        private val connectionRepository: ConnectionRepository,
    ) {
        suspend operator fun invoke() {
            connectionRepository.disconnect()
        }
    }
