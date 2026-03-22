package studio.etsoftware.obdapp.domain.usecase

import studio.etsoftware.obdapp.domain.repository.DiagnosticsRepository
import javax.inject.Inject

class ClearTroubleCodesUseCase
    @Inject
    constructor(
        private val diagnosticsRepository: DiagnosticsRepository,
    ) {
        suspend operator fun invoke(): Result<Unit> {
            return diagnosticsRepository.clearTroubleCodes()
        }
    }
