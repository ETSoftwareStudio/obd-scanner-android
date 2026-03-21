package studio.etsoftware.obdapp.domain.usecase

import studio.etsoftware.obdapp.domain.repository.ObdRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class ClearTroubleCodesUseCaseTest {
    private val repository: ObdRepository = mockk()
    private lateinit var useCase: ClearTroubleCodesUseCase

    @Before
    fun setup() {
        useCase = ClearTroubleCodesUseCase(repository)
    }

    @Test
    fun `invoke clears trouble codes on success`() =
        runTest {
            coEvery { repository.clearTroubleCodes() } returns Result.success(Unit)

            val result = useCase()

            coVerify { repository.clearTroubleCodes() }
            assertTrue(result.isSuccess)
        }

    @Test
    fun `invoke returns failure when repository fails`() =
        runTest {
            coEvery { repository.clearTroubleCodes() } returns Result.failure(Exception("Clear failed"))

            val result = useCase()

            assertTrue(result.isFailure)
        }
}
