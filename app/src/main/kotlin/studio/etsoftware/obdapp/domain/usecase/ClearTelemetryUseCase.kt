package studio.etsoftware.obdapp.domain.usecase

import studio.etsoftware.obdapp.domain.repository.TelemetryRepository
import javax.inject.Inject

class ClearTelemetryUseCase
    @Inject
    constructor(
        private val telemetryRepository: TelemetryRepository,
    ) {
        suspend operator fun invoke() {
            telemetryRepository.clear()
        }
    }
