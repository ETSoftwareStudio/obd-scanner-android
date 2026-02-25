package com.eltonvs.obdapp.domain.usecase

import com.eltonvs.obdapp.domain.model.DeviceInfo
import com.eltonvs.obdapp.domain.model.DeviceType
import com.eltonvs.obdapp.domain.repository.ObdRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class ConnectDeviceUseCaseTest {
    private val repository: ObdRepository = mockk()
    private lateinit var useCase: ConnectDeviceUseCase

    private val testDevice =
        DeviceInfo(
            address = "AA:BB:CC:DD:EE:FF",
            name = "Test OBD Device",
            type = DeviceType.CLASSIC,
        )

    @Before
    fun setup() {
        useCase = ConnectDeviceUseCase(repository)
    }

    @Test
    fun `invoke calls repository connect`() =
        runTest {
            coEvery { repository.connect(testDevice) } returns Result.success(Unit)

            val result = useCase(testDevice)

            coVerify { repository.connect(testDevice) }
            assertEquals(true, result.isSuccess)
        }

    @Test
    fun `invoke returns failure when repository fails`() =
        runTest {
            coEvery { repository.connect(testDevice) } returns Result.failure(Exception("Connection failed"))

            val result = useCase(testDevice)

            assertEquals(true, result.isFailure)
        }
}
