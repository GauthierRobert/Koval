package com.koval.trainingplanner.ui.training

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.koval.trainingplanner.data.repository.TrainingRepository
import com.koval.trainingplanner.domain.model.Training
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class TrainingDetailUiState(
    val training: Training? = null,
    val isLoading: Boolean = true,
    val error: String? = null,
)

@HiltViewModel
class TrainingDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val trainingRepository: TrainingRepository,
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
                _uiState.update { it.copy(training = training, isLoading = false) }
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoading = false, error = e.message) }
            }
        }
    }
}
