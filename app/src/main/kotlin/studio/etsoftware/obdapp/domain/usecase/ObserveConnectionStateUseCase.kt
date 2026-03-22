package studio.etsoftware.obdapp.domain.usecase

import javax.inject.Inject
import kotlinx.coroutines.flow.StateFlow
import studio.etsoftware.obdapp.domain.model.ConnectionState
import studio.etsoftware.obdapp.domain.repository.ConnectionRepository

class ObserveConnectionStateUseCase
    @Inject
    constructor(
        private val connectionRepository: ConnectionRepository,
    ) {
        operator fun invoke(): StateFlow<ConnectionState> = connectionRepository.connectionState
    }
