package com.koval.trainingplanner.ui.training

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.koval.trainingplanner.data.repository.TrainingRepository
import com.koval.trainingplanner.domain.model.ReceivedTraining
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

/** A named training source: "My Trainings" or the origin name from a received training. */
data class TrainingSourceContext(
    val key: String,     // "mine" or the originName
    val label: String,   // Display label
)

data class TrainingListUiState(
    val myTrainings: List<Training> = emptyList(),
    val receivedTrainings: List<ReceivedTraining> = emptyList(),
    val sourceContexts: List<TrainingSourceContext> = listOf(TrainingSourceContext("mine", "My Trainings")),
    val activeSource: String = "mine",
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

    // Cache of trainings resolved by ID from received trainings
    private val resolvedTrainings = mutableMapOf<String, Training>()

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
                val received = try { trainingRepository.listReceivedTrainings() } catch (_: Exception) { emptyList() }

                // Build source contexts from distinct origin names
                val contexts = buildList {
                    add(TrainingSourceContext("mine", "My Trainings"))
                    received.mapNotNull { it.originName }
                        .distinct()
                        .sorted()
                        .forEach { name -> add(TrainingSourceContext(name, name)) }
                }

                _uiState.update {
                    it.copy(
                        myTrainings = my,
                        receivedTrainings = received,
                        sourceContexts = contexts,
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

    fun setActiveSource(key: String) {
        _uiState.update { it.copy(activeSource = key) }
        // Resolve trainings for the selected source if needed
        if (key != "mine") {
            resolveReceivedTrainings(key)
        }
    }

    fun setSportFilter(sport: SportType?) {
        _uiState.update { it.copy(sportFilter = if (it.sportFilter == sport) null else sport) }
    }

    fun setTypeFilter(type: TrainingType?) {
        _uiState.update { it.copy(typeFilter = if (it.typeFilter == type) null else type) }
    }

    fun filteredTrainings(): List<Training> {
        val state = _uiState.value
        val base = if (state.activeSource == "mine") {
            state.myTrainings
        } else {
            // Get training IDs for this source, resolve from cache
            state.receivedTrainings
                .filter { it.originName == state.activeSource }
                .mapNotNull { resolvedTrainings[it.trainingId] }
        }
        return base.filter { training ->
            (state.sportFilter == null || training.sportType == state.sportFilter) &&
                (state.typeFilter == null || training.trainingType == state.typeFilter)
        }
    }

    private fun resolveReceivedTrainings(originName: String) {
        val state = _uiState.value
        val trainingIds = state.receivedTrainings
            .filter { it.originName == originName }
            .map { it.trainingId }
            .filter { it !in resolvedTrainings }

        if (trainingIds.isEmpty()) return

        viewModelScope.launch {
            for (id in trainingIds) {
                try {
                    val training = trainingRepository.getTraining(id)
                    resolvedTrainings[id] = training
                } catch (_: Exception) {
                    // Skip unresolvable trainings
                }
            }
            // Trigger recomposition
            _uiState.update { it.copy() }
        }
    }
}
