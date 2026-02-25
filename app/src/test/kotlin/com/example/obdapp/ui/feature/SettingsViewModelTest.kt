package com.eltonvs.obdapp.ui.feature.settings

import com.eltonvs.obdapp.util.PreferencesManager
import io.mockk.coEvery
import io.mockk.coVerify
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

    private lateinit var viewModel: SettingsViewModel

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)

        coEvery { preferencesManager.pollingInterval } returns MutableStateFlow(1000L)
        coEvery { preferencesManager.theme } returns MutableStateFlow("dark")

        viewModel = SettingsViewModel(preferencesManager)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `initial state has expected values`() {
        assertEquals(1000L, viewModel.uiState.value.pollingInterval)
        assertEquals("dark", viewModel.uiState.value.theme)
    }

    @Test
    fun `setPollingInterval calls preferences manager`() =
        runTest {
            coEvery { preferencesManager.setPollingInterval(any()) } returns Unit

            viewModel.setPollingInterval(2000L)
            testDispatcher.scheduler.advanceUntilIdle()

            coVerify { preferencesManager.setPollingInterval(2000L) }
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
