package com.eltonvs.obdapp.ui.feature.connection

import com.eltonvs.obdapp.domain.model.ConnectionState
import com.eltonvs.obdapp.domain.model.DeviceInfo
import com.eltonvs.obdapp.domain.model.DeviceType
import com.eltonvs.obdapp.domain.repository.ObdRepository
import com.eltonvs.obdapp.domain.usecase.ConnectDeviceUseCase
import com.eltonvs.obdapp.domain.usecase.GetPairedDevicesUseCase
import com.eltonvs.obdapp.util.PreferencesManager
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
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

    private val testDevices =
        listOf(
            DeviceInfo("AA:BB:CC:DD:EE:FF", "OBD Device 1", DeviceType.CLASSIC),
            DeviceInfo("11:22:33:44:55:66", "OBD Device 2", DeviceType.CLASSIC),
        )

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)

        every { repository.connectionState } returns MutableStateFlow(ConnectionState.Disconnected)
        coEvery { preferencesManager.lastDeviceAddress } returns flowOf(null)
        coEvery { preferencesManager.lastDeviceName } returns flowOf(null)

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
    fun `loadPairedDevices updates device list`() =
        runTest {
            coEvery { getPairedDevicesUseCase() } returns testDevices

            viewModel.loadPairedDevices()
            testDispatcher.scheduler.advanceUntilIdle()

            assertEquals(testDevices, viewModel.uiState.value.devices)
        }

    @Test
    fun `selectDevice updates selected device`() {
        val device = testDevices.first()

        viewModel.selectDevice(device)

        assertEquals(device, viewModel.uiState.value.selectedDevice)
    }

    @Test
    fun `connect with selected device calls connect use case`() =
        runTest {
            val device = testDevices.first()
            viewModel.selectDevice(device)

            coEvery { connectDeviceUseCase(device) } returns Result.success(Unit)

            viewModel.connect()
            testDispatcher.scheduler.advanceUntilIdle()

            coVerify { connectDeviceUseCase(device) }
        }

    @Test
    fun `connect failure updates error state`() =
        runTest {
            val device = testDevices.first()
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
