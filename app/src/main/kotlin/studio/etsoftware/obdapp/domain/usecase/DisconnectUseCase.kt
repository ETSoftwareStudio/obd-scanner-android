package studio.etsoftware.obdapp.domain.usecase

import studio.etsoftware.obdapp.domain.repository.ObdRepository
import javax.inject.Inject

class DisconnectUseCase
    @Inject
    constructor(
        private val repository: ObdRepository,
    ) {
        suspend operator fun invoke() {
            repository.disconnect()
        }
    }
