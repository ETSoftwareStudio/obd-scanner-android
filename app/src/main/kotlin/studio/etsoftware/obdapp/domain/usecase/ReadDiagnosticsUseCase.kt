package studio.etsoftware.obdapp.domain.usecase

import studio.etsoftware.obdapp.domain.model.DiagnosticInfo
import studio.etsoftware.obdapp.domain.repository.ObdRepository
import javax.inject.Inject

class ReadDiagnosticsUseCase
    @Inject
    constructor(
        private val repository: ObdRepository,
    ) {
        suspend operator fun invoke(): Result<DiagnosticInfo> {
            return repository.readDiagnosticInfo()
        }
    }
