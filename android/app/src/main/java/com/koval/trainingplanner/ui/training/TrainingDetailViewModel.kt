package com.koval.trainingplanner.ui.training

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.koval.trainingplanner.data.repository.AuthRepository
import com.koval.trainingplanner.data.repository.TrainingRepository
import com.koval.trainingplanner.domain.model.Training
import com.koval.trainingplanner.domain.model.User
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class TrainingDetailUiState(
    val training: Training? = null,
    val user: User? = null,
    val isLoading: Boolean = true,
    val error: String? = null,
)

@HiltViewModel
class TrainingDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val trainingRepository: TrainingRepository,
    private val authRepository: AuthRepository,
) : ViewModel() {

    private val trainingId: String = savedStateHandle["trainingId"] ?: ""

    private val _uiState = MutableStateFlow(TrainingDetailUiState())
    val uiState: StateFlow<TrainingDetailUiState> = _uiState.asStateFlow()

    init {
        loadTraining()
    }

    private fun loadTraining() {
        if (trainingId.isBlank()) {
            _uiState.update { it.copy(isLoading = false, error = "No training ID") }
            return
        }
        viewModelScope.launch {
            try {
                val training = trainingRepository.getTraining(trainingId)
                val user = try { authRepository.fetchCurrentUser() } catch (_: Exception) { null }
                _uiState.update { it.copy(training = training, user = user, isLoading = false) }
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoading = false, error = e.message) }
            }
        }
    }
}
