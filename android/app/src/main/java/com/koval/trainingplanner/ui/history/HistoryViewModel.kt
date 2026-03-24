package com.koval.trainingplanner.ui.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.koval.trainingplanner.data.repository.SessionRepository
import com.koval.trainingplanner.domain.model.CompletedSession
import com.koval.trainingplanner.domain.model.PmcDataPoint
import com.koval.trainingplanner.domain.model.SportType
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import javax.inject.Inject

data class HistoryUiState(
    val sessions: List<CompletedSession> = emptyList(),
    val pmcLatest: PmcDataPoint? = null,
    val sportFilter: SportType? = null,
    val isLoading: Boolean = false,
    val isRefreshing: Boolean = false,
    val error: String? = null,
)

@HiltViewModel
class HistoryViewModel @Inject constructor(
    private val sessionRepository: SessionRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(HistoryUiState())
    val uiState: StateFlow<HistoryUiState> = _uiState.asStateFlow()

    init {
        loadSessions()
    }

    fun loadSessions(isRefresh: Boolean = false) {
        viewModelScope.launch {
            _uiState.update {
                it.copy(isLoading = !isRefresh, isRefreshing = isRefresh, error = null)
            }
            try {
                val sessions = sessionRepository.listSessions()
                // Load PMC for latest fitness/fatigue values
                val pmcLatest = try {
                    val today = LocalDate.now()
                    val from = today.minusDays(90).format(DateTimeFormatter.ISO_LOCAL_DATE)
                    val to = today.format(DateTimeFormatter.ISO_LOCAL_DATE)
                    val pmc = sessionRepository.getPmc(from, to)
                    pmc.lastOrNull { !it.predicted }
                } catch (_: Exception) { null }

                _uiState.update {
                    it.copy(
                        sessions = sessions,
                        pmcLatest = pmcLatest,
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

    fun setSportFilter(sport: SportType?) {
        _uiState.update { it.copy(sportFilter = if (it.sportFilter == sport) null else sport) }
    }

    fun filteredSessions(): List<CompletedSession> {
        val state = _uiState.value
        return if (state.sportFilter == null) {
            state.sessions
        } else {
            state.sessions.filter { it.sportType == state.sportFilter }
        }
    }
}
