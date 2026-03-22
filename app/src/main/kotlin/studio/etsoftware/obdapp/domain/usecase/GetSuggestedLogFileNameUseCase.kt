package studio.etsoftware.obdapp.domain.usecase

import javax.inject.Inject
import studio.etsoftware.obdapp.domain.repository.LogExportRepository

class GetSuggestedLogFileNameUseCase
    @Inject
    constructor(
        private val logExportRepository: LogExportRepository,
    ) {
        operator fun invoke(nowMillis: Long = System.currentTimeMillis()): String {
            return logExportRepository.buildDefaultFileName(nowMillis)
        }
    }
