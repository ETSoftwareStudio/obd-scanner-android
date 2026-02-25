package com.example.obdapp.domain.usecase

import com.example.obdapp.domain.model.DiagnosticInfo
import com.example.obdapp.domain.repository.ObdRepository
import javax.inject.Inject

class ReadDiagnosticsUseCase @Inject constructor(
    private val repository: ObdRepository
) {
    suspend operator fun invoke(): Result<DiagnosticInfo> {
        return repository.readDiagnosticInfo()
    }
}
