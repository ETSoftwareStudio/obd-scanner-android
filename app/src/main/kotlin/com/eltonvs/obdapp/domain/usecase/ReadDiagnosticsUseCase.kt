package com.eltonvs.obdapp.domain.usecase

import com.eltonvs.obdapp.domain.model.DiagnosticInfo
import com.eltonvs.obdapp.domain.repository.ObdRepository
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
