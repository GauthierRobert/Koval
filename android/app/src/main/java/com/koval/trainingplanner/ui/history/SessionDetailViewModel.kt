package com.koval.trainingplanner.ui.history

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.koval.trainingplanner.data.repository.SessionRepository
import com.koval.trainingplanner.domain.model.CompletedSession
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SessionDetailUiState(
    val session: CompletedSession? = null,
    val isLoading: Boolean = true,
    val error: String? = null,
)

@HiltViewModel
class SessionDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val sessionRepository: SessionRepository,
) : ViewModel() {

    private val sessionId: String = savedStateHandle["sessionId"] ?: ""

    private val _uiState = MutableStateFlow(SessionDetailUiState())
    val uiState: StateFlow<SessionDetailUiState> = _uiState.asStateFlow()

    init {
        loadSession()
    }

    private fun loadSession() {
        if (sessionId.isBlank()) {
            _uiState.update { it.copy(isLoading = false, error = "No session ID") }
            return
        }
        viewModelScope.launch {
            try {
                val session = sessionRepository.getSession(sessionId)
                _uiState.update { it.copy(session = session, isLoading = false) }
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoading = false, error = e.message) }
            }
        }
    }

    fun updateRpe(rpe: Int) {
        viewModelScope.launch {
            try {
                val updated = sessionRepository.updateRpe(sessionId, rpe)
                _uiState.update { it.copy(session = updated) }
            } catch (_: Exception) {}
        }
    }
}
