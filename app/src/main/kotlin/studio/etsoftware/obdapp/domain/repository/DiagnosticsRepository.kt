package studio.etsoftware.obdapp.domain.repository

import studio.etsoftware.obdapp.domain.model.DiagnosticInfo

interface DiagnosticsRepository {
    suspend fun readDiagnosticInfo(): Result<DiagnosticInfo>

    suspend fun clearTroubleCodes(): Result<Unit>
}
