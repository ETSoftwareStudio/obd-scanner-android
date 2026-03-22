package studio.etsoftware.obdapp.ui.feature.diagnostics

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import studio.etsoftware.obdapp.domain.model.ConnectionState
import studio.etsoftware.obdapp.domain.model.DiagnosticInfo
import studio.etsoftware.obdapp.domain.usecase.ClearTroubleCodesUseCase
import studio.etsoftware.obdapp.domain.usecase.ObserveConnectionStateUseCase
import studio.etsoftware.obdapp.domain.usecase.ReadDiagnosticsUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class DiagnosticsUiState(
    val diagnosticInfo: DiagnosticInfo? = null,
    val isLoading: Boolean = false,
    val error: String? = null,
    val connectionState: ConnectionState = ConnectionState.Disconnected,
)

@HiltViewModel
class DiagnosticsViewModel
    @Inject
    constructor(
        private val readDiagnosticsUseCase: ReadDiagnosticsUseCase,
        private val clearTroubleCodesUseCase: ClearTroubleCodesUseCase,
        private val observeConnectionStateUseCase: ObserveConnectionStateUseCase,
    ) : ViewModel() {
        private val _uiState = MutableStateFlow(DiagnosticsUiState())
        val uiState: StateFlow<DiagnosticsUiState> = _uiState.asStateFlow()

        private var hasLoadedForCurrentConnection = false
        private var wasConnected = false

        init {
            observeConnectionState()
        }

        private fun observeConnectionState() {
            viewModelScope.launch {
                observeConnectionStateUseCase().collect { state ->
                    _uiState.update { it.copy(connectionState = state) }

                    val isConnected = state is ConnectionState.Connected
                    if (isConnected && !wasConnected) {
                        readDiagnosticsIfNeeded()
                    }
                    if (!isConnected) {
                        hasLoadedForCurrentConnection = false
                    }

                    wasConnected = isConnected
                }
            }
        }

        private fun readDiagnosticsIfNeeded() {
            if (hasLoadedForCurrentConnection || _uiState.value.isLoading) {
                return
            }
            readDiagnostics()
        }

        fun readDiagnostics() {
            viewModelScope.launch {
                _uiState.update { it.copy(isLoading = true, error = null) }
                loadDiagnostics()
            }
        }

        fun clearTroubleCodes() {
            viewModelScope.launch {
                _uiState.update { it.copy(isLoading = true, error = null) }

                clearTroubleCodesUseCase().fold(
                    onSuccess = {
                        loadDiagnostics()
                    },
                    onFailure = { e ->
                        _uiState.update { it.copy(error = e.message, isLoading = false) }
                    },
                )
            }
        }

        fun clearError() {
            _uiState.update { it.copy(error = null) }
        }

        private suspend fun loadDiagnostics() {
            readDiagnosticsUseCase().fold(
                onSuccess = { info ->
                    hasLoadedForCurrentConnection = true
                    _uiState.update { it.copy(diagnosticInfo = info, isLoading = false) }
                },
                onFailure = { e ->
                    _uiState.update { it.copy(error = e.message, isLoading = false) }
                },
            )
        }
    }
