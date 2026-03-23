package studio.etsoftware.obdapp.domain.usecase

import javax.inject.Inject
import studio.etsoftware.obdapp.domain.repository.LogExportRepository

class ExportLogsUseCase
    @Inject
    constructor(
        private val logExportRepository: LogExportRepository,
    ) {
        suspend operator fun invoke(
            destination: String,
            content: String,
        ): Result<Unit> = logExportRepository.export(destination, content)
    }
