package studio.etsoftware.obdapp.domain.usecase

import studio.etsoftware.obdapp.domain.model.DeviceInfo
import studio.etsoftware.obdapp.domain.model.DeviceType
import studio.etsoftware.obdapp.domain.repository.ObdRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class GetPairedDevicesUseCaseTest {
    private val repository: ObdRepository = mockk()
    private lateinit var useCase: GetPairedDevicesUseCase

    private val testDevices =
        listOf(
            DeviceInfo("AA:BB:CC:DD:EE:FF", "Device 1", DeviceType.CLASSIC),
            DeviceInfo("11:22:33:44:55:66", "Device 2", DeviceType.CLASSIC),
        )

    @Before
    fun setup() {
        useCase = GetPairedDevicesUseCase(repository)
    }

    @Test
    fun `invoke returns list of paired devices`() =
        runTest {
            coEvery { repository.getPairedDevices() } returns testDevices

            val result = useCase()

            coVerify { repository.getPairedDevices() }
            assertEquals(testDevices, result)
        }

    @Test
    fun `invoke returns empty list when no devices paired`() =
        runTest {
            coEvery { repository.getPairedDevices() } returns emptyList()

            val result = useCase()

            assertEquals(emptyList<DeviceInfo>(), result)
        }
}
