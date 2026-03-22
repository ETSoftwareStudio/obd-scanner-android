package studio.etsoftware.obdapp.ui.feature.dashboard

import android.net.Uri
import app.cash.turbine.test
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import studio.etsoftware.obdapp.domain.model.ConnectionState
import studio.etsoftware.obdapp.domain.model.DashboardMetricsSnapshot
import studio.etsoftware.obdapp.domain.model.DebugLogEntry
import studio.etsoftware.obdapp.domain.model.DebugLogType
import studio.etsoftware.obdapp.domain.model.TelemetryEvent
import studio.etsoftware.obdapp.domain.repository.AppSettingsRepository
import studio.etsoftware.obdapp.domain.repository.ConnectionRepository
import studio.etsoftware.obdapp.domain.repository.DashboardRepository
import studio.etsoftware.obdapp.domain.repository.DebugLogRepository
import studio.etsoftware.obdapp.domain.repository.LogExportRepository
import studio.etsoftware.obdapp.domain.repository.PollingSettingsRepository
import studio.etsoftware.obdapp.domain.repository.TelemetryRepository
import studio.etsoftware.obdapp.domain.usecase.BuildLogExportTextUseCase
import studio.etsoftware.obdapp.domain.usecase.ClearDebugLogsUseCase
import studio.etsoftware.obdapp.domain.usecase.ClearTelemetryUseCase
import studio.etsoftware.obdapp.domain.usecase.ConnectDeviceUseCase
import studio.etsoftware.obdapp.domain.usecase.DisconnectUseCase
import studio.etsoftware.obdapp.domain.usecase.ExportLogsUseCase
import studio.etsoftware.obdapp.domain.usecase.GetSuggestedLogFileNameUseCase
import studio.etsoftware.obdapp.domain.usecase.ObserveAutoConnectUseCase
import studio.etsoftware.obdapp.domain.usecase.ObserveConnectionStateUseCase
import studio.etsoftware.obdapp.domain.usecase.ObserveDashboardMetricsUseCase
import studio.etsoftware.obdapp.domain.usecase.ObserveDebugLogsUseCase
import studio.etsoftware.obdapp.domain.usecase.ObserveLastDeviceUseCase
import studio.etsoftware.obdapp.domain.usecase.ObservePollingIntervalUseCase
import studio.etsoftware.obdapp.domain.usecase.ObserveTelemetryEventsUseCase
import studio.etsoftware.obdapp.domain.usecase.ObserveWasConnectedUseCase
import studio.etsoftware.obdapp.domain.usecase.StartDashboardPollingUseCase
import studio.etsoftware.obdapp.domain.usecase.StopDashboardPollingUseCase

@OptIn(ExperimentalCoroutinesApi::class)
class DashboardViewModelTest {
    private val testDispatcher = StandardTestDispatcher()

    private val dashboardRepository: DashboardRepository = mockk(relaxed = true)
    private val connectionRepository: ConnectionRepository = mockk(relaxed = true)
    private val appSettingsRepository: AppSettingsRepository = mockk(relaxed = true)
    private val pollingSettingsRepository: PollingSettingsRepository = mockk(relaxed = true)
    private val debugLogRepository: DebugLogRepository = mockk(relaxed = true)
    private val logExportRepository: LogExportRepository = mockk(relaxed = true)
    private val telemetryRepository: TelemetryRepository = mockk(relaxed = true)

    private lateinit var viewModel: DashboardViewModel
    private lateinit var connectionStateFlow: MutableStateFlow<ConnectionState>
    private lateinit var logsFlow: MutableStateFlow<List<DebugLogEntry>>
    private lateinit var telemetryEventsFlow: MutableStateFlow<List<TelemetryEvent>>
    private lateinit var pollingIntervalFlow: MutableStateFlow<Long>
    private lateinit var dashboardMetricsFlow: MutableStateFlow<DashboardMetricsSnapshot>

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)

        connectionStateFlow = MutableStateFlow(ConnectionState.Disconnected)
        logsFlow = MutableStateFlow(emptyList())
        telemetryEventsFlow = MutableStateFlow(emptyList())
        pollingIntervalFlow = MutableStateFlow(1000L)
        dashboardMetricsFlow = MutableStateFlow(DashboardMetricsSnapshot())

        every { connectionRepository.connectionState } returns connectionStateFlow
        every { appSettingsRepository.autoConnect } returns MutableStateFlow(false)
        every { appSettingsRepository.wasConnected } returns MutableStateFlow(false)
        every { appSettingsRepository.lastDevice } returns MutableStateFlow(null)
        every { pollingSettingsRepository.pollingInterval } returns pollingIntervalFlow
        every { dashboardRepository.dashboardMetrics } returns dashboardMetricsFlow
        every { debugLogRepository.logs } returns logsFlow
        every { telemetryRepository.events } returns telemetryEventsFlow
        every { logExportRepository.buildDefaultFileName(any()) } returns "obd-debug-log.txt"
        every { logExportRepository.buildExportText(any(), any(), any()) } returns "export"

        viewModel =
            DashboardViewModel(
                observeDashboardMetricsUseCase = ObserveDashboardMetricsUseCase(dashboardRepository),
                startDashboardPollingUseCase = StartDashboardPollingUseCase(dashboardRepository),
                stopDashboardPollingUseCase = StopDashboardPollingUseCase(dashboardRepository),
                disconnectUseCase = DisconnectUseCase(connectionRepository),
                connectDeviceUseCase = ConnectDeviceUseCase(connectionRepository),
                observeConnectionStateUseCase = ObserveConnectionStateUseCase(connectionRepository),
                observeAutoConnectUseCase = ObserveAutoConnectUseCase(appSettingsRepository),
                observeWasConnectedUseCase = ObserveWasConnectedUseCase(appSettingsRepository),
                observeLastDeviceUseCase = ObserveLastDeviceUseCase(appSettingsRepository),
                observeDebugLogsUseCase = ObserveDebugLogsUseCase(debugLogRepository),
                clearDebugLogsUseCase = ClearDebugLogsUseCase(debugLogRepository),
                getSuggestedLogFileNameUseCase = GetSuggestedLogFileNameUseCase(logExportRepository),
                buildLogExportTextUseCase = BuildLogExportTextUseCase(logExportRepository),
                exportLogsUseCase = ExportLogsUseCase(logExportRepository),
                observePollingIntervalUseCase = ObservePollingIntervalUseCase(pollingSettingsRepository),
                observeTelemetryEventsUseCase = ObserveTelemetryEventsUseCase(telemetryRepository),
                clearTelemetryUseCase = ClearTelemetryUseCase(telemetryRepository),
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
            logsFlow.value = listOf(DebugLogEntry("10:00:00.000", DebugLogType.INFO, "Connected"))
            coEvery { logExportRepository.export(uri.toString(), any()) } returns Result.success(Unit)

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
            logsFlow.value = listOf(DebugLogEntry("10:00:00.000", DebugLogType.INFO, "Connected"))
            coEvery { logExportRepository.export(uri.toString(), any()) } returns Result.failure(Exception("disk full"))

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

            coVerify(exactly = 0) { logExportRepository.export(any(), any()) }
        }

    @Test
    fun `connection loss clears polling flag`() =
        runTest {
            coEvery { dashboardRepository.startPolling(any()) } returns Unit

            connectionStateFlow.value = ConnectionState.Connected
            advanceUntilIdle()
            assertEquals(true, viewModel.uiState.value.isPolling)

            connectionStateFlow.value = ConnectionState.Disconnected
            advanceUntilIdle()

            assertEquals(false, viewModel.uiState.value.isPolling)
        }

    @Test
    fun `polling interval change restarts active polling`() =
        runTest {
            coEvery { dashboardRepository.startPolling(any()) } returns Unit

            connectionStateFlow.value = ConnectionState.Connected
            advanceUntilIdle()

            pollingIntervalFlow.value = 2000L
            advanceUntilIdle()

            coVerify { dashboardRepository.startPolling(1000L) }
            coVerify { dashboardRepository.startPolling(2000L) }
        }

    @Test
    fun `clearLogs delegates to debug log and telemetry repositories`() =
        runTest {
            coEvery { debugLogRepository.clear() } returns Unit
            coEvery { telemetryRepository.clear() } returns Unit

            viewModel.clearLogs()
            advanceUntilIdle()

            coVerify { debugLogRepository.clear() }
            coVerify { telemetryRepository.clear() }
        }

    @Test
    fun `disconnect stops polling before disconnecting`() =
        runTest {
            coEvery { dashboardRepository.stopPolling() } returns Unit
            coEvery { connectionRepository.disconnect() } returns Unit

            viewModel.disconnect()
            advanceUntilIdle()

            io.mockk.coVerifyOrder {
                dashboardRepository.stopPolling()
                connectionRepository.disconnect()
            }
        }
}
