package com.koval.trainingplanner.ui.training

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.koval.trainingplanner.data.repository.TrainingRepository
import com.koval.trainingplanner.domain.model.SportType
import com.koval.trainingplanner.domain.model.Training
import com.koval.trainingplanner.domain.model.TrainingType
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class TrainingSource { MY, CLUB }

data class TrainingListUiState(
    val myTrainings: List<Training> = emptyList(),
    val clubTrainings: List<Training> = emptyList(),
    val sourceFilter: TrainingSource = TrainingSource.MY,
    val sportFilter: SportType? = null,
    val typeFilter: TrainingType? = null,
    val isLoading: Boolean = false,
    val isRefreshing: Boolean = false,
    val error: String? = null,
)

@HiltViewModel
class TrainingListViewModel @Inject constructor(
    private val trainingRepository: TrainingRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(TrainingListUiState())
    val uiState: StateFlow<TrainingListUiState> = _uiState.asStateFlow()

    init {
        loadTrainings()
    }

    fun loadTrainings(isRefresh: Boolean = false) {
        viewModelScope.launch {
            _uiState.update {
                it.copy(isLoading = !isRefresh, isRefreshing = isRefresh, error = null)
            }
            try {
                val my = trainingRepository.listTrainings()
                val club = try { trainingRepository.listClubTrainings() } catch (_: Exception) { emptyList() }
                _uiState.update {
                    it.copy(
                        myTrainings = my,
                        clubTrainings = club,
                        isLoading = false,
                        isRefreshing = false,
                    )
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(isLoading = false, isRefreshing = false, error = e.message)
                }
            }
        }
    }

    fun setSourceFilter(source: TrainingSource) {
        _uiState.update { it.copy(sourceFilter = source) }
    }

    fun setSportFilter(sport: SportType?) {
        _uiState.update { it.copy(sportFilter = if (it.sportFilter == sport) null else sport) }
    }

    fun setTypeFilter(type: TrainingType?) {
        _uiState.update { it.copy(typeFilter = if (it.typeFilter == type) null else type) }
    }

    fun filteredTrainings(): List<Training> {
        val state = _uiState.value
        val base = when (state.sourceFilter) {
            TrainingSource.MY -> state.myTrainings
            TrainingSource.CLUB -> state.clubTrainings
        }
        return base.filter { training ->
            (state.sportFilter == null || training.sportType == state.sportFilter) &&
                (state.typeFilter == null || training.trainingType == state.typeFilter)
        }
    }
}
