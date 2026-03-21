package studio.etsoftware.obdapp.domain.usecase

import studio.etsoftware.obdapp.domain.repository.ObdRepository
import javax.inject.Inject

class ClearTroubleCodesUseCase
    @Inject
    constructor(
        private val repository: ObdRepository,
    ) {
        suspend operator fun invoke(): Result<Unit> {
            return repository.clearTroubleCodes()
        }
    }
