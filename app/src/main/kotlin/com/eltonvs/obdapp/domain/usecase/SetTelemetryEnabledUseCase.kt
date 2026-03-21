package com.eltonvs.obdapp.domain.usecase

import com.eltonvs.obdapp.domain.repository.TelemetryRepository
import javax.inject.Inject

class SetTelemetryEnabledUseCase
    @Inject
    constructor(
        private val telemetryRepository: TelemetryRepository,
    ) {
        suspend operator fun invoke(enabled: Boolean) {
            telemetryRepository.setEnabled(enabled)
        }
    }
