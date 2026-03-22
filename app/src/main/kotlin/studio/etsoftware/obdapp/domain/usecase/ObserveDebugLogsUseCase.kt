package studio.etsoftware.obdapp.domain.usecase

import javax.inject.Inject
import kotlinx.coroutines.flow.StateFlow
import studio.etsoftware.obdapp.domain.model.DebugLogEntry
import studio.etsoftware.obdapp.domain.repository.DebugLogRepository

class ObserveDebugLogsUseCase
    @Inject
    constructor(
        private val debugLogRepository: DebugLogRepository,
    ) {
        operator fun invoke(): StateFlow<List<DebugLogEntry>> = debugLogRepository.logs
    }
