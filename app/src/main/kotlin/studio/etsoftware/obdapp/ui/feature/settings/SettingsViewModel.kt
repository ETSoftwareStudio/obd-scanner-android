package studio.etsoftware.obdapp.ui.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import studio.etsoftware.obdapp.domain.usecase.ObserveAutoConnectUseCase
import studio.etsoftware.obdapp.domain.usecase.ObservePollingIntervalUseCase
import studio.etsoftware.obdapp.domain.usecase.ObserveTelemetryEnabledUseCase
import studio.etsoftware.obdapp.domain.usecase.ObserveThemeUseCase
import studio.etsoftware.obdapp.domain.usecase.SetAutoConnectUseCase
import studio.etsoftware.obdapp.domain.usecase.SetPollingIntervalUseCase
import studio.etsoftware.obdapp.domain.usecase.SetTelemetryEnabledUseCase
import studio.etsoftware.obdapp.domain.usecase.SetThemeUseCase
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
        private val observeThemeUseCase: ObserveThemeUseCase,
        private val setThemeUseCase: SetThemeUseCase,
        private val observeAutoConnectUseCase: ObserveAutoConnectUseCase,
        private val setAutoConnectUseCase: SetAutoConnectUseCase,
        private val observePollingIntervalUseCase: ObservePollingIntervalUseCase,
        private val setPollingIntervalUseCase: SetPollingIntervalUseCase,
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
                observePollingIntervalUseCase().collect { interval ->
                    _uiState.update { it.copy(pollingInterval = interval) }
                }
            }
            viewModelScope.launch {
                observeThemeUseCase().collect { theme ->
                    _uiState.update { it.copy(theme = theme) }
                }
            }
            viewModelScope.launch {
                observeAutoConnectUseCase().collect { autoConnect ->
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
                setPollingIntervalUseCase(interval)
            }
        }

        fun setTheme(theme: String) {
            viewModelScope.launch {
                setThemeUseCase(theme)
            }
        }

        fun setAutoConnect(enabled: Boolean) {
            viewModelScope.launch {
                setAutoConnectUseCase(enabled)
            }
        }

        fun setTelemetryEnabled(enabled: Boolean) {
            viewModelScope.launch {
                setTelemetryEnabledUseCase(enabled)
            }
        }
    }
