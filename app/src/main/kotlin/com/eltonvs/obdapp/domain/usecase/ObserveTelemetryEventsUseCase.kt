package com.eltonvs.obdapp.domain.usecase

import com.eltonvs.obdapp.domain.model.TelemetryEvent
import com.eltonvs.obdapp.domain.repository.TelemetryRepository
import javax.inject.Inject
import kotlinx.coroutines.flow.StateFlow

class ObserveTelemetryEventsUseCase
    @Inject
    constructor(
        private val telemetryRepository: TelemetryRepository,
    ) {
        operator fun invoke(): StateFlow<List<TelemetryEvent>> = telemetryRepository.events
    }
