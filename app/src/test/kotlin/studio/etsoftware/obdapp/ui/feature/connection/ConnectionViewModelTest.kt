package studio.etsoftware.obdapp.ui.feature.connection

import studio.etsoftware.obdapp.domain.model.ConnectionState
import studio.etsoftware.obdapp.domain.model.DeviceInfo
import studio.etsoftware.obdapp.domain.model.DeviceType
import studio.etsoftware.obdapp.domain.model.DiscoveryState
import studio.etsoftware.obdapp.domain.model.PairingState
import studio.etsoftware.obdapp.domain.repository.ObdRepository
import studio.etsoftware.obdapp.domain.usecase.ConnectDeviceUseCase
import studio.etsoftware.obdapp.domain.usecase.GetPairedDevicesUseCase
import studio.etsoftware.obdapp.util.PreferencesManager
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import io.mockk.verify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ConnectionViewModelTest {
    private val testDispatcher = StandardTestDispatcher()

    private val getPairedDevicesUseCase: GetPairedDevicesUseCase = mockk(relaxed = true)
    private val connectDeviceUseCase: ConnectDeviceUseCase = mockk(relaxed = true)
    private val repository: ObdRepository = mockk(relaxed = true)
    private val preferencesManager: PreferencesManager = mockk(relaxed = true)

    private lateinit var viewModel: ConnectionViewModel
    private lateinit var connectionStateFlow: MutableStateFlow<ConnectionState>
    private lateinit var discoveryStateFlow: MutableStateFlow<DiscoveryState>
    private lateinit var pairingStateFlow: MutableStateFlow<PairingState>

    private val pairedDevices =
        listOf(
            DeviceInfo("AA:BB:CC:DD:EE:FF", "OBD Device 1", DeviceType.CLASSIC),
            DeviceInfo("11:22:33:44:55:66", "OBD Device 2", DeviceType.CLASSIC),
        )
    private val discoveredDevice = DeviceInfo("77:88:99:AA:BB:CC", "New OBD Device", DeviceType.CLASSIC)

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)

        connectionStateFlow = MutableStateFlow(ConnectionState.Disconnected)
        discoveryStateFlow = MutableStateFlow(DiscoveryState.Idle)
        pairingStateFlow = MutableStateFlow(PairingState.Idle)

        every { repository.connectionState } returns connectionStateFlow
        every { repository.discoveryState } returns discoveryStateFlow
        every { repository.pairingState } returns pairingStateFlow
        every { repository.isBluetoothEnabled() } returns true
        every { repository.isLocationServicesEnabledForDiscovery() } returns true
        every { repository.startDiscovery() } returns Result.success(Unit)
        every { repository.stopDiscovery() } just runs
        every { repository.pairDevice(any()) } returns Result.success(Unit)
        every { repository.clearPairingState() } just runs
        every { preferencesManager.lastDeviceAddress } returns flowOf(null)
        every { preferencesManager.lastDeviceName } returns flowOf(null)

        viewModel =
            ConnectionViewModel(
                getPairedDevicesUseCase,
                connectDeviceUseCase,
                repository,
                preferencesManager,
            )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `loadPairedDevices updates paired device list`() =
        runTest {
            coEvery { getPairedDevicesUseCase() } returns pairedDevices

            viewModel.loadPairedDevices()
            testDispatcher.scheduler.advanceUntilIdle()

            assertEquals(pairedDevices, viewModel.uiState.value.pairedDevices)
        }

    @Test
    fun `discovery updates nearby devices excluding already paired items`() =
        runTest {
            coEvery { getPairedDevicesUseCase() } returns pairedDevices

            viewModel.loadPairedDevices()
            testDispatcher.scheduler.advanceUntilIdle()

            discoveryStateFlow.value = DiscoveryState.Discovering(listOf(pairedDevices.first(), discoveredDevice))
            testDispatcher.scheduler.advanceUntilIdle()

            assertEquals(listOf(discoveredDevice), viewModel.uiState.value.nearbyDevices)
        }

    @Test
    fun `selectDevice updates selected device`() {
        val device = pairedDevices.first()

        viewModel.selectDevice(device)

        assertEquals(device, viewModel.uiState.value.selectedDevice)
    }

    @Test
    fun `startDiscovery failure updates error state`() {
        every { repository.startDiscovery() } returns Result.failure(Exception("Discovery failed"))

        viewModel.startDiscovery()

        assertEquals("Discovery failed", viewModel.uiState.value.error)
    }

    @Test
    fun `pairDevice failure updates error state`() {
        every { repository.pairDevice(discoveredDevice) } returns Result.failure(Exception("Pairing failed"))

        viewModel.pairDevice(discoveredDevice)

        assertEquals("Pairing failed", viewModel.uiState.value.error)
    }

    @Test
    fun `pairing success refreshes paired devices and selects device`() =
        runTest {
            val updatedPairedDevices = pairedDevices + discoveredDevice
            coEvery { getPairedDevicesUseCase() } returnsMany listOf(pairedDevices, updatedPairedDevices)

            viewModel.loadPairedDevices()
            testDispatcher.scheduler.advanceUntilIdle()

            pairingStateFlow.value = PairingState.Paired(discoveredDevice)
            testDispatcher.scheduler.advanceUntilIdle()

            assertEquals(updatedPairedDevices, viewModel.uiState.value.pairedDevices)
            assertEquals(discoveredDevice.address, viewModel.uiState.value.selectedDevice?.address)
            assertEquals("${discoveredDevice.name} paired successfully", viewModel.uiState.value.message)
            verify { repository.clearPairingState() }
        }

    @Test
    fun `connect with selected device stops discovery and calls connect use case`() =
        runTest {
            val device = pairedDevices.first()
            viewModel.selectDevice(device)

            coEvery { connectDeviceUseCase(device) } returns Result.success(Unit)

            viewModel.connect()
            testDispatcher.scheduler.advanceUntilIdle()

            verify { repository.stopDiscovery() }
            coVerify { connectDeviceUseCase(device) }
        }

    @Test
    fun `connect failure updates error state`() =
        runTest {
            val device = pairedDevices.first()
            viewModel.selectDevice(device)

            coEvery { connectDeviceUseCase(device) } returns Result.failure(Exception("Connection failed"))

            viewModel.connect()
            testDispatcher.scheduler.advanceUntilIdle()

            assertTrue(viewModel.uiState.value.error != null)
        }

    @Test
    fun `clearError clears error state`() {
        viewModel.clearError()

        assertEquals(null, viewModel.uiState.value.error)
    }
}
