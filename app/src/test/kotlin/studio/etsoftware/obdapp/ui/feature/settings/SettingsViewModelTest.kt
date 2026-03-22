package studio.etsoftware.obdapp.ui.feature.settings

import studio.etsoftware.obdapp.domain.repository.AppSettingsRepository
import studio.etsoftware.obdapp.domain.repository.PollingSettingsRepository
import studio.etsoftware.obdapp.domain.repository.TelemetryRepository
import studio.etsoftware.obdapp.domain.usecase.ObserveAutoConnectUseCase
import studio.etsoftware.obdapp.domain.usecase.ObservePollingIntervalUseCase
import studio.etsoftware.obdapp.domain.usecase.ObserveTelemetryEnabledUseCase
import studio.etsoftware.obdapp.domain.usecase.ObserveThemeUseCase
import studio.etsoftware.obdapp.domain.usecase.SetAutoConnectUseCase
import studio.etsoftware.obdapp.domain.usecase.SetPollingIntervalUseCase
import studio.etsoftware.obdapp.domain.usecase.SetTelemetryEnabledUseCase
import studio.etsoftware.obdapp.domain.usecase.SetThemeUseCase
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SettingsViewModelTest {
    private val testDispatcher = StandardTestDispatcher()

    private val appSettingsRepository: AppSettingsRepository = mockk(relaxed = true)
    private val pollingSettingsRepository: PollingSettingsRepository = mockk(relaxed = true)
    private val telemetryRepository: TelemetryRepository = mockk(relaxed = true)

    private lateinit var viewModel: SettingsViewModel

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)

        every { pollingSettingsRepository.pollingInterval } returns MutableStateFlow(1000L)
        every { appSettingsRepository.theme } returns MutableStateFlow("dark")
        every { appSettingsRepository.autoConnect } returns MutableStateFlow(true)
        every { telemetryRepository.isEnabled } returns MutableStateFlow(true)

        viewModel =
            SettingsViewModel(
                ObserveThemeUseCase(appSettingsRepository),
                SetThemeUseCase(appSettingsRepository),
                ObserveAutoConnectUseCase(appSettingsRepository),
                SetAutoConnectUseCase(appSettingsRepository),
                ObservePollingIntervalUseCase(pollingSettingsRepository),
                SetPollingIntervalUseCase(pollingSettingsRepository),
                ObserveTelemetryEnabledUseCase(telemetryRepository),
                SetTelemetryEnabledUseCase(telemetryRepository),
            )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `initial state has expected values`() =
        runTest {
            testDispatcher.scheduler.advanceUntilIdle()
            assertEquals(1000L, viewModel.uiState.value.pollingInterval)
            assertEquals("dark", viewModel.uiState.value.theme)
        }

    @Test
    fun `setPollingInterval calls polling settings repository`() =
        runTest {
            coEvery { pollingSettingsRepository.setPollingInterval(any()) } returns Unit

            viewModel.setPollingInterval(2000L)
            testDispatcher.scheduler.advanceUntilIdle()

            coVerify { pollingSettingsRepository.setPollingInterval(2000L) }
        }

    @Test
    fun `setTheme calls app settings repository`() =
        runTest {
            coEvery { appSettingsRepository.setTheme(any()) } returns Unit

            viewModel.setTheme("light")
            testDispatcher.scheduler.advanceUntilIdle()

            coVerify { appSettingsRepository.setTheme("light") }
        }

    @Test
    fun `setTelemetryEnabled calls telemetry repository`() =
        runTest {
            coEvery { telemetryRepository.setEnabled(any()) } returns Unit

            viewModel.setTelemetryEnabled(false)
            testDispatcher.scheduler.advanceUntilIdle()

            coVerify { telemetryRepository.setEnabled(false) }
        }

    @Test
    fun `available intervals contains expected values`() {
        val expectedIntervals = listOf(500L, 1000L, 2000L, 5000L)
        assertEquals(expectedIntervals, viewModel.uiState.value.availableIntervals)
    }

    @Test
    fun `available themes contains expected values`() {
        val expectedThemes = listOf("system", "light", "dark")
        assertEquals(expectedThemes, viewModel.uiState.value.availableThemes)
    }
}
