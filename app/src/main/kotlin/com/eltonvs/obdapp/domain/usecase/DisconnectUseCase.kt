package com.eltonvs.obdapp.domain.usecase

import com.eltonvs.obdapp.domain.repository.ObdRepository
import javax.inject.Inject

class DisconnectUseCase
    @Inject
    constructor(
        private val repository: ObdRepository,
    ) {
        suspend operator fun invoke() {
            repository.disconnect()
        }
    }
