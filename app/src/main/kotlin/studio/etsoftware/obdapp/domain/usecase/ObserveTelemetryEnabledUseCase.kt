package studio.etsoftware.obdapp.domain.usecase

import studio.etsoftware.obdapp.domain.repository.TelemetryRepository
import javax.inject.Inject
import kotlinx.coroutines.flow.StateFlow

class ObserveTelemetryEnabledUseCase
    @Inject
    constructor(
        private val telemetryRepository: TelemetryRepository,
    ) {
        operator fun invoke(): StateFlow<Boolean> = telemetryRepository.isEnabled
    }
