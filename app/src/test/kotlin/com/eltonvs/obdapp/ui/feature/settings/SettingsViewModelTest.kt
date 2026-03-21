package com.eltonvs.obdapp.ui.feature.settings

import com.eltonvs.obdapp.domain.repository.PollingSettingsRepository
import com.eltonvs.obdapp.domain.repository.TelemetryRepository
import com.eltonvs.obdapp.domain.usecase.ObservePollingIntervalUseCase
import com.eltonvs.obdapp.domain.usecase.ObserveTelemetryEnabledUseCase
import com.eltonvs.obdapp.domain.usecase.SetPollingIntervalUseCase
import com.eltonvs.obdapp.domain.usecase.SetTelemetryEnabledUseCase
import com.eltonvs.obdapp.util.PreferencesManager
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

    private val preferencesManager: PreferencesManager = mockk(relaxed = true)
    private val pollingSettingsRepository: PollingSettingsRepository = mockk(relaxed = true)
    private val telemetryRepository: TelemetryRepository = mockk(relaxed = true)

    private lateinit var viewModel: SettingsViewModel

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)

        every { pollingSettingsRepository.pollingInterval } returns MutableStateFlow(1000L)
        every { preferencesManager.theme } returns MutableStateFlow("dark")
        every { preferencesManager.autoConnect } returns MutableStateFlow(true)
        every { telemetryRepository.isEnabled } returns MutableStateFlow(true)

        viewModel =
            SettingsViewModel(
                preferencesManager,
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
    fun `setTheme calls preferences manager`() =
        runTest {
            coEvery { preferencesManager.setTheme(any()) } returns Unit

            viewModel.setTheme("light")
            testDispatcher.scheduler.advanceUntilIdle()

            coVerify { preferencesManager.setTheme("light") }
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
