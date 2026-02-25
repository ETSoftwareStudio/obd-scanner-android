package com.eltonvs.obdapp.domain.usecase

import com.eltonvs.obdapp.domain.model.DiagnosticInfo
import com.eltonvs.obdapp.domain.model.TroubleCode
import com.eltonvs.obdapp.domain.model.TroubleCodeType
import com.eltonvs.obdapp.domain.repository.ObdRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class ReadDiagnosticsUseCaseTest {

    private val repository: ObdRepository = mockk()
    private lateinit var useCase: ReadDiagnosticsUseCase

    private val testDiagnosticInfo = DiagnosticInfo(
        vin = "1HGBH41JXMN109186",
        troubleCodes = listOf(
            TroubleCode("P0301", "Cylinder 1 Misfire Detected", TroubleCodeType.CURRENT)
        ),
        milStatus = true,
        dtcCount = 1
    )

    @Before
    fun setup() {
        useCase = ReadDiagnosticsUseCase(repository)
    }

    @Test
    fun `invoke returns diagnostic info on success`() = runTest {
        coEvery { repository.readDiagnosticInfo() } returns Result.success(testDiagnosticInfo)

        val result = useCase()

        coVerify { repository.readDiagnosticInfo() }
        assertTrue(result.isSuccess)
        assertEquals(testDiagnosticInfo, result.getOrNull())
    }

    @Test
    fun `invoke returns failure when repository fails`() = runTest {
        coEvery { repository.readDiagnosticInfo() } returns Result.failure(Exception("Not connected"))

        val result = useCase()

        assertTrue(result.isFailure)
    }
}
