package studio.etsoftware.obdapp.ui.feature.connection

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
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import studio.etsoftware.obdapp.domain.model.ConnectionState
import studio.etsoftware.obdapp.domain.model.DeviceInfo
import studio.etsoftware.obdapp.domain.model.DeviceType
import studio.etsoftware.obdapp.domain.model.DiscoveryState
import studio.etsoftware.obdapp.domain.model.PairingState
import studio.etsoftware.obdapp.domain.repository.AppSettingsRepository
import studio.etsoftware.obdapp.domain.repository.ConnectionRepository
import studio.etsoftware.obdapp.domain.repository.DiscoveryRepository
import studio.etsoftware.obdapp.domain.usecase.ClearPairingStateUseCase
import studio.etsoftware.obdapp.domain.usecase.ConnectDeviceUseCase
import studio.etsoftware.obdapp.domain.usecase.GetPairedDevicesUseCase
import studio.etsoftware.obdapp.domain.usecase.IsBluetoothEnabledUseCase
import studio.etsoftware.obdapp.domain.usecase.IsLocationServicesEnabledForDiscoveryUseCase
import studio.etsoftware.obdapp.domain.usecase.ObserveConnectionStateUseCase
import studio.etsoftware.obdapp.domain.usecase.ObserveDiscoveryStateUseCase
import studio.etsoftware.obdapp.domain.usecase.ObserveLastDeviceUseCase
import studio.etsoftware.obdapp.domain.usecase.ObservePairingStateUseCase
import studio.etsoftware.obdapp.domain.usecase.PairDeviceUseCase
import studio.etsoftware.obdapp.domain.usecase.SaveLastDeviceUseCase
import studio.etsoftware.obdapp.domain.usecase.SetWasConnectedUseCase
import studio.etsoftware.obdapp.domain.usecase.StartDiscoveryUseCase
import studio.etsoftware.obdapp.domain.usecase.StopDiscoveryUseCase

@OptIn(ExperimentalCoroutinesApi::class)
class ConnectionViewModelTest {
    private val testDispatcher = StandardTestDispatcher()

    private val connectionRepository: ConnectionRepository = mockk(relaxed = true)
    private val discoveryRepository: DiscoveryRepository = mockk(relaxed = true)
    private val appSettingsRepository: AppSettingsRepository = mockk(relaxed = true)

    private lateinit var viewModel: ConnectionViewModel
    private lateinit var connectionStateFlow: MutableStateFlow<ConnectionState>
    private lateinit var discoveryStateFlow: MutableStateFlow<DiscoveryState>
    private lateinit var pairingStateFlow: MutableStateFlow<PairingState>
    private lateinit var lastDeviceFlow: MutableStateFlow<DeviceInfo?>

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
        lastDeviceFlow = MutableStateFlow(null)

        every { connectionRepository.connectionState } returns connectionStateFlow
        every { discoveryRepository.discoveryState } returns discoveryStateFlow
        every { discoveryRepository.pairingState } returns pairingStateFlow
        every { discoveryRepository.isBluetoothEnabled() } returns true
        every { discoveryRepository.isLocationServicesEnabledForDiscovery() } returns true
        every { discoveryRepository.startDiscovery() } returns Result.success(Unit)
        every { discoveryRepository.stopDiscovery() } just runs
        every { discoveryRepository.pairDevice(any()) } returns Result.success(Unit)
        every { appSettingsRepository.lastDevice } returns lastDeviceFlow
        coEvery { appSettingsRepository.setWasConnected(any()) } returns Unit
        coEvery { appSettingsRepository.setLastDevice(any()) } returns Unit

        viewModel =
            ConnectionViewModel(
                getPairedDevicesUseCase = GetPairedDevicesUseCase(connectionRepository),
                connectDeviceUseCase = ConnectDeviceUseCase(connectionRepository),
                observeConnectionStateUseCase = ObserveConnectionStateUseCase(connectionRepository),
                observeDiscoveryStateUseCase = ObserveDiscoveryStateUseCase(discoveryRepository),
                observePairingStateUseCase = ObservePairingStateUseCase(discoveryRepository),
                observeLastDeviceUseCase = ObserveLastDeviceUseCase(appSettingsRepository),
                saveLastDeviceUseCase = SaveLastDeviceUseCase(appSettingsRepository),
                setWasConnectedUseCase = SetWasConnectedUseCase(appSettingsRepository),
                isBluetoothEnabledUseCase = IsBluetoothEnabledUseCase(discoveryRepository),
                isLocationServicesEnabledForDiscoveryUseCase = IsLocationServicesEnabledForDiscoveryUseCase(discoveryRepository),
                startDiscoveryUseCase = StartDiscoveryUseCase(discoveryRepository),
                stopDiscoveryUseCase = StopDiscoveryUseCase(discoveryRepository),
                pairDeviceUseCase = PairDeviceUseCase(discoveryRepository),
                clearPairingStateUseCase = ClearPairingStateUseCase(discoveryRepository),
            )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `loadPairedDevices updates paired device list`() =
        runTest {
            coEvery { connectionRepository.getPairedDevices() } returns pairedDevices

            viewModel.loadPairedDevices()
            testDispatcher.scheduler.advanceUntilIdle()

            assertEquals(pairedDevices, viewModel.uiState.value.pairedDevices)
        }

    @Test
    fun `discovery updates nearby devices excluding already paired items`() =
        runTest {
            coEvery { connectionRepository.getPairedDevices() } returns pairedDevices

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
        every { discoveryRepository.startDiscovery() } returns Result.failure(Exception("Discovery failed"))

        viewModel.startDiscovery()

        assertEquals("Discovery failed", viewModel.uiState.value.error)
    }

    @Test
    fun `pairDevice failure updates error state`() {
        every { discoveryRepository.pairDevice(discoveredDevice) } returns Result.failure(Exception("Pairing failed"))

        viewModel.pairDevice(discoveredDevice)

        assertEquals("Pairing failed", viewModel.uiState.value.error)
    }

    @Test
    fun `pairing success refreshes paired devices and selects device`() =
        runTest {
            val updatedPairedDevices = pairedDevices + discoveredDevice
            coEvery { connectionRepository.getPairedDevices() } returnsMany listOf(pairedDevices, updatedPairedDevices)

            viewModel.loadPairedDevices()
            testDispatcher.scheduler.advanceUntilIdle()

            pairingStateFlow.value = PairingState.Paired(discoveredDevice)
            testDispatcher.scheduler.advanceUntilIdle()

            assertEquals(updatedPairedDevices, viewModel.uiState.value.pairedDevices)
            assertEquals(discoveredDevice.address, viewModel.uiState.value.selectedDevice?.address)
            assertEquals("${discoveredDevice.name} paired successfully", viewModel.uiState.value.message)
            verify { discoveryRepository.clearPairingState() }
        }

    @Test
    fun `connect with selected device stops discovery and calls connect use case`() =
        runTest {
            val device = pairedDevices.first()
            viewModel.selectDevice(device)

            coEvery { connectionRepository.connect(device) } returns Result.success(Unit)

            viewModel.connect()
            testDispatcher.scheduler.advanceUntilIdle()

            verify { discoveryRepository.stopDiscovery() }
            coVerify { connectionRepository.connect(device) }
        }

    @Test
    fun `connect failure updates error state`() =
        runTest {
            val device = pairedDevices.first()
            viewModel.selectDevice(device)

            coEvery { connectionRepository.connect(device) } returns Result.failure(Exception("Connection failed"))

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
