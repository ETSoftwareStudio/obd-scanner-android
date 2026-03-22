package studio.etsoftware.obdapp.domain.usecase

import javax.inject.Inject
import studio.etsoftware.obdapp.domain.repository.DebugLogRepository

class ClearDebugLogsUseCase
    @Inject
    constructor(
        private val debugLogRepository: DebugLogRepository,
    ) {
        suspend operator fun invoke() {
            debugLogRepository.clear()
        }
    }
