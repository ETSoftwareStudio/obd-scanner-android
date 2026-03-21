package com.eltonvs.obdapp.domain.usecase

import com.eltonvs.obdapp.domain.repository.ObdRepository
import javax.inject.Inject

class ClearTroubleCodesUseCase
    @Inject
    constructor(
        private val repository: ObdRepository,
    ) {
        suspend operator fun invoke(): Result<Unit> {
            return repository.clearTroubleCodes()
        }
    }
