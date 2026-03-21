package com.eltonvs.obdapp.ui.feature.diagnostics

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.eltonvs.obdapp.domain.model.ConnectionState
import com.eltonvs.obdapp.domain.model.DiagnosticInfo
import com.eltonvs.obdapp.domain.repository.ObdRepository
import com.eltonvs.obdapp.domain.usecase.ClearTroubleCodesUseCase
import com.eltonvs.obdapp.domain.usecase.ReadDiagnosticsUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

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
        private val repository: ObdRepository,
    ) : ViewModel() {
        private val _uiState = MutableStateFlow(DiagnosticsUiState())
        val uiState: StateFlow<DiagnosticsUiState> = _uiState.asStateFlow()

        init {
            observeConnectionState()
        }

        private fun observeConnectionState() {
            viewModelScope.launch {
                repository.connectionState.collect { state ->
                    _uiState.update { it.copy(connectionState = state) }
                }
            }
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
                    _uiState.update { it.copy(diagnosticInfo = info, isLoading = false) }
                },
                onFailure = { e ->
                    _uiState.update { it.copy(error = e.message, isLoading = false) }
                },
            )
        }
    }
