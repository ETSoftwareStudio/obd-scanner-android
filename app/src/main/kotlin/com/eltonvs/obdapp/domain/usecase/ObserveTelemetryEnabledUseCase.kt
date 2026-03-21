package com.eltonvs.obdapp.domain.usecase

import com.eltonvs.obdapp.domain.repository.TelemetryRepository
import javax.inject.Inject
import kotlinx.coroutines.flow.StateFlow

class ObserveTelemetryEnabledUseCase
    @Inject
    constructor(
        private val telemetryRepository: TelemetryRepository,
    ) {
        operator fun invoke(): StateFlow<Boolean> = telemetryRepository.isEnabled
    }
