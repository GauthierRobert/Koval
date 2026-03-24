package com.koval.trainingplanner.ui.builder

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.koval.trainingplanner.data.repository.TrainingRepository
import com.koval.trainingplanner.domain.model.BlockType
import com.koval.trainingplanner.domain.model.SportType
import com.koval.trainingplanner.domain.model.Training
import com.koval.trainingplanner.domain.model.TrainingType
import com.koval.trainingplanner.domain.model.WorkoutElement
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject
import kotlin.math.roundToInt

data class BuilderUiState(
    val title: String = "",
    val description: String = "",
    val sportType: SportType = SportType.CYCLING,
    val trainingType: TrainingType? = null,
    val blocks: List<WorkoutElement> = emptyList(),
    val editingBlockIndex: Int = -1, // -1 = adding new
    val isEditMode: Boolean = false, // editing existing training
    val existingTrainingId: String? = null,
    val isSaving: Boolean = false,
    val isLoading: Boolean = false,
    val saveSuccess: Boolean = false,
    val error: String? = null,
) {
    val estimatedDurationSeconds: Int get() = computeDuration(blocks)
    val estimatedTss: Int get() = computeTss(blocks).roundToInt()
}

@HiltViewModel
class WorkoutBuilderViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val trainingRepository: TrainingRepository,
) : ViewModel() {

    private val trainingId: String? = savedStateHandle.get<String>("trainingId")?.takeIf { it.isNotBlank() }

    private val _uiState = MutableStateFlow(BuilderUiState())
    val uiState: StateFlow<BuilderUiState> = _uiState.asStateFlow()

    init {
        if (trainingId != null) {
            loadExistingTraining(trainingId)
        }
    }

    private fun loadExistingTraining(id: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            try {
                val training = trainingRepository.getTraining(id)
                _uiState.update {
                    it.copy(
                        title = training.title,
                        description = training.description ?: "",
                        sportType = training.sportType,
                        trainingType = training.trainingType,
                        blocks = training.blocks,
                        isEditMode = true,
                        existingTrainingId = id,
                        isLoading = false,
                    )
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoading = false, error = e.message) }
            }
        }
    }

    fun setTitle(title: String) {
        _uiState.update { it.copy(title = title) }
    }

    fun setDescription(desc: String) {
        _uiState.update { it.copy(description = desc) }
    }

    fun setSportType(sport: SportType) {
        _uiState.update { it.copy(sportType = sport) }
    }

    fun setTrainingType(type: TrainingType?) {
        _uiState.update { it.copy(trainingType = if (it.trainingType == type) null else type) }
    }

    fun addBlock(block: WorkoutElement) {
        _uiState.update { it.copy(blocks = it.blocks + block) }
    }

    fun updateBlock(index: Int, block: WorkoutElement) {
        _uiState.update { state ->
            val newBlocks = state.blocks.toMutableList()
            if (index in newBlocks.indices) {
                newBlocks[index] = block
            }
            state.copy(blocks = newBlocks, editingBlockIndex = -1)
        }
    }

    fun removeBlock(index: Int) {
        _uiState.update { state ->
            state.copy(blocks = state.blocks.filterIndexed { i, _ -> i != index })
        }
    }

    fun moveBlock(from: Int, to: Int) {
        _uiState.update { state ->
            val newBlocks = state.blocks.toMutableList()
            if (from in newBlocks.indices && to in newBlocks.indices) {
                val item = newBlocks.removeAt(from)
                newBlocks.add(to, item)
            }
            state.copy(blocks = newBlocks)
        }
    }

    fun duplicateBlock(index: Int) {
        _uiState.update { state ->
            if (index in state.blocks.indices) {
                val newBlocks = state.blocks.toMutableList()
                newBlocks.add(index + 1, state.blocks[index])
                state.copy(blocks = newBlocks)
            } else state
        }
    }

    fun setEditingBlock(index: Int) {
        _uiState.update { it.copy(editingBlockIndex = index) }
    }

    fun groupAsSet(indices: Set<Int>, repetitions: Int, restDurationSeconds: Int, restIntensity: Int) {
        _uiState.update { state ->
            val sorted = indices.sorted()
            if (sorted.isEmpty()) return@update state
            val selectedBlocks = sorted.map { state.blocks[it] }
            val set = WorkoutElement(
                repetitions = repetitions,
                elements = selectedBlocks,
                restDurationSeconds = restDurationSeconds,
                restIntensity = restIntensity,
            )
            val newBlocks = state.blocks.filterIndexed { i, _ -> i !in indices }.toMutableList()
            val insertAt = (sorted.first()).coerceAtMost(newBlocks.size)
            newBlocks.add(insertAt, set)
            state.copy(blocks = newBlocks)
        }
    }

    fun save() {
        val state = _uiState.value
        if (state.title.isBlank()) {
            _uiState.update { it.copy(error = "Title is required") }
            return
        }
        if (state.blocks.isEmpty()) {
            _uiState.update { it.copy(error = "Add at least one block") }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true, error = null) }
            try {
                val training = Training(
                    id = state.existingTrainingId ?: "",
                    title = state.title,
                    description = state.description.takeIf { it.isNotBlank() },
                    sportType = state.sportType,
                    trainingType = state.trainingType,
                    blocks = state.blocks,
                    estimatedTss = state.estimatedTss,
                    estimatedDurationSeconds = state.estimatedDurationSeconds,
                )
                if (state.isEditMode && state.existingTrainingId != null) {
                    trainingRepository.updateTraining(state.existingTrainingId, training)
                } else {
                    trainingRepository.createTraining(training)
                }
                _uiState.update { it.copy(isSaving = false, saveSuccess = true) }
            } catch (e: Exception) {
                _uiState.update { it.copy(isSaving = false, error = e.message) }
            }
        }
    }
}

// ── TSS / Duration computation (matches Angular) ──

private fun computeTss(blocks: List<WorkoutElement>): Double {
    return blocks.sumOf { computeElementTss(it) }
}

private fun computeElementTss(element: WorkoutElement): Double {
    if (element.isSet) {
        val reps = element.repetitions ?: 1
        val childTss = element.elements?.sumOf { computeElementTss(it) } ?: 0.0
        val restTss = if (element.restDurationSeconds != null && element.restDurationSeconds > 0) {
            val restIntensity = (element.restIntensity ?: 40).toDouble()
            element.restDurationSeconds * (restIntensity / 100.0) * (restIntensity / 100.0) / 36.0
        } else 0.0
        return reps * childTss + (reps - 1) * restTss
    }
    val duration = element.durationSeconds ?: 0
    val intensity = when (element.type) {
        BlockType.RAMP -> ((element.intensityStart ?: 50) + (element.intensityEnd ?: 50)) / 2.0
        BlockType.FREE, BlockType.PAUSE -> 50.0
        else -> (element.intensityTarget ?: 50).toDouble()
    }
    return duration * (intensity / 100.0) * (intensity / 100.0) / 36.0
}

private fun computeDuration(blocks: List<WorkoutElement>): Int {
    return blocks.sumOf { computeElementDuration(it) }
}

private fun computeElementDuration(element: WorkoutElement): Int {
    if (element.isSet) {
        val reps = element.repetitions ?: 1
        val childDuration = element.elements?.sumOf { computeElementDuration(it) } ?: 0
        val restDuration = element.restDurationSeconds ?: 0
        return reps * childDuration + (reps - 1) * restDuration
    }
    return element.durationSeconds ?: 0
}
