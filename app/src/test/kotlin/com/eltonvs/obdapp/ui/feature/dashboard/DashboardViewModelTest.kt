package com.eltonvs.obdapp.ui.feature.dashboard

import android.net.Uri
import app.cash.turbine.test
import com.eltonvs.obdapp.domain.model.ConnectionState
import com.eltonvs.obdapp.domain.model.DashboardMetricsSnapshot
import com.eltonvs.obdapp.domain.model.TelemetryEvent
import com.eltonvs.obdapp.domain.repository.ObdRepository
import com.eltonvs.obdapp.domain.repository.TelemetryRepository
import com.eltonvs.obdapp.domain.usecase.ClearTelemetryUseCase
import com.eltonvs.obdapp.domain.usecase.ConnectDeviceUseCase
import com.eltonvs.obdapp.domain.usecase.DisconnectUseCase
import com.eltonvs.obdapp.domain.usecase.ObserveTelemetryEventsUseCase
import com.eltonvs.obdapp.domain.usecase.ReadMetricsUseCase
import com.eltonvs.obdapp.util.LogEntry
import com.eltonvs.obdapp.util.LogExportFormatter
import com.eltonvs.obdapp.util.LogExporter
import com.eltonvs.obdapp.util.LogManager
import com.eltonvs.obdapp.util.LogType
import com.eltonvs.obdapp.util.PreferencesManager
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
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class DashboardViewModelTest {
    private val testDispatcher = StandardTestDispatcher()

    private val readMetricsUseCase: ReadMetricsUseCase = mockk(relaxed = true)
    private val disconnectUseCase: DisconnectUseCase = mockk(relaxed = true)
    private val connectDeviceUseCase: ConnectDeviceUseCase = mockk(relaxed = true)
    private val repository: ObdRepository = mockk(relaxed = true)
    private val preferencesManager: PreferencesManager = mockk(relaxed = true)
    private val logExporter: LogExporter = mockk(relaxed = true)
    private val logManager: LogManager = mockk(relaxed = true)
    private val telemetryRepository: TelemetryRepository = mockk(relaxed = true)
    private val logExportFormatter = LogExportFormatter()

    private lateinit var viewModel: DashboardViewModel
    private lateinit var connectionStateFlow: MutableStateFlow<ConnectionState>
    private lateinit var logsFlow: MutableStateFlow<List<LogEntry>>
    private lateinit var telemetryEventsFlow: MutableStateFlow<List<TelemetryEvent>>

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)

        connectionStateFlow = MutableStateFlow(ConnectionState.Disconnected)
        logsFlow = MutableStateFlow(emptyList())
        telemetryEventsFlow = MutableStateFlow(emptyList())

        every { repository.connectionState } returns connectionStateFlow
        every { preferencesManager.autoConnect } returns flowOf(false)
        every { preferencesManager.wasConnected } returns flowOf(false)
        every { preferencesManager.lastDeviceAddress } returns flowOf(null)
        every { preferencesManager.lastDeviceName } returns flowOf(null)
        every { preferencesManager.pollingInterval } returns flowOf(1000L)
        every { readMetricsUseCase.invoke() } returns MutableStateFlow(DashboardMetricsSnapshot())
        every { logManager.logs } returns logsFlow
        every { logManager.clear() } just runs
        every { telemetryRepository.events } returns telemetryEventsFlow

        viewModel =
            DashboardViewModel(
                readMetricsUseCase,
                disconnectUseCase,
                connectDeviceUseCase,
                repository,
                preferencesManager,
                logExportFormatter,
                logExporter,
                logManager,
                ObserveTelemetryEventsUseCase(telemetryRepository),
                ClearTelemetryUseCase(telemetryRepository),
            )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `exportLogs emits success event when exporter succeeds`() =
        runTest {
            val uri: Uri = mockk(relaxed = true)
            logsFlow.value = listOf(LogEntry("10:00:00.000", LogType.INFO, "Connected"))
            coEvery { logExporter.export(uri, any()) } returns Result.success(Unit)

            viewModel.events.test {
                viewModel.exportLogs(uri)
                advanceUntilIdle()

                assertEquals(DashboardEvent.ExportSuccess, awaitItem())
            }
        }

    @Test
    fun `exportLogs emits error event when exporter fails`() =
        runTest {
            val uri: Uri = mockk(relaxed = true)
            logsFlow.value = listOf(LogEntry("10:00:00.000", LogType.INFO, "Connected"))
            coEvery { logExporter.export(uri, any()) } returns Result.failure(Exception("disk full"))

            viewModel.events.test {
                viewModel.exportLogs(uri)
                advanceUntilIdle()

                assertEquals(DashboardEvent.ExportError("disk full"), awaitItem())
            }
        }

    @Test
    fun `exportLogs emits skipped event when there are no logs`() =
        runTest {
            val uri: Uri = mockk(relaxed = true)
            logsFlow.value = emptyList()

            viewModel.events.test {
                viewModel.exportLogs(uri)
                advanceUntilIdle()

                assertEquals(DashboardEvent.ExportSkippedNoLogs, awaitItem())
            }

            coVerify(exactly = 0) { logExporter.export(any(), any()) }
        }

    @Test
    fun `connection loss clears polling flag`() =
        runTest {
            connectionStateFlow.value = ConnectionState.Connected
            advanceUntilIdle()
            assertEquals(true, viewModel.uiState.value.isPolling)

            connectionStateFlow.value = ConnectionState.Disconnected
            advanceUntilIdle()

            assertEquals(false, viewModel.uiState.value.isPolling)
        }

    @Test
    fun `clearLogs delegates to log manager and clears telemetry`() =
        runTest {
            coEvery { telemetryRepository.clear() } returns Unit

            viewModel.clearLogs()
            advanceUntilIdle()

            verify { logManager.clear() }
            coVerify { telemetryRepository.clear() }
        }
}
