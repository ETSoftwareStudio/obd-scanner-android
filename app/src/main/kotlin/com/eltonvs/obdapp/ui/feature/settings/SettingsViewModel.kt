package com.eltonvs.obdapp.ui.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.eltonvs.obdapp.domain.usecase.ObserveTelemetryEnabledUseCase
import com.eltonvs.obdapp.domain.usecase.SetTelemetryEnabledUseCase
import com.eltonvs.obdapp.util.PreferencesManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SettingsUiState(
    val pollingInterval: Long = 1000L,
    val theme: String = "system",
    val autoConnect: Boolean = true,
    val telemetryEnabled: Boolean = true,
    val availableIntervals: List<Long> = listOf(500L, 1000L, 2000L, 5000L),
    val availableThemes: List<String> = listOf("system", "light", "dark"),
)

@HiltViewModel
class SettingsViewModel
    @Inject
    constructor(
        private val preferencesManager: PreferencesManager,
        private val observeTelemetryEnabledUseCase: ObserveTelemetryEnabledUseCase,
        private val setTelemetryEnabledUseCase: SetTelemetryEnabledUseCase,
    ) : ViewModel() {
        private val _uiState = MutableStateFlow(SettingsUiState())
        val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

        init {
            loadSettings()
        }

        private fun loadSettings() {
            viewModelScope.launch {
                preferencesManager.pollingInterval.collect { interval ->
                    _uiState.update { it.copy(pollingInterval = interval) }
                }
            }
            viewModelScope.launch {
                preferencesManager.theme.collect { theme ->
                    _uiState.update { it.copy(theme = theme) }
                }
            }
            viewModelScope.launch {
                preferencesManager.autoConnect.collect { autoConnect ->
                    _uiState.update { it.copy(autoConnect = autoConnect) }
                }
            }
            viewModelScope.launch {
                observeTelemetryEnabledUseCase().collect { telemetryEnabled ->
                    _uiState.update { it.copy(telemetryEnabled = telemetryEnabled) }
                }
            }
        }

        fun setPollingInterval(interval: Long) {
            viewModelScope.launch {
                preferencesManager.setPollingInterval(interval)
            }
        }

        fun setTheme(theme: String) {
            viewModelScope.launch {
                preferencesManager.setTheme(theme)
            }
        }

        fun setAutoConnect(enabled: Boolean) {
            viewModelScope.launch {
                preferencesManager.setAutoConnect(enabled)
            }
        }

        fun setTelemetryEnabled(enabled: Boolean) {
            viewModelScope.launch {
                setTelemetryEnabledUseCase(enabled)
            }
        }
    }
