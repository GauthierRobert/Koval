package com.koval.trainingplanner.ui.zones

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.koval.trainingplanner.data.repository.AuthRepository
import com.koval.trainingplanner.data.repository.ZoneRepository
import com.koval.trainingplanner.domain.model.SportType
import com.koval.trainingplanner.domain.model.User
import com.koval.trainingplanner.domain.model.ZoneSystem
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ZonesUiState(
    val zoneSystems: List<ZoneSystem> = emptyList(),
    val user: User? = null,
    val selectedSport: SportType? = null,
    val isLoading: Boolean = false,
    val error: String? = null,
)

@HiltViewModel
class ZonesViewModel @Inject constructor(
    private val zoneRepository: ZoneRepository,
    private val authRepository: AuthRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(ZonesUiState())
    val uiState: StateFlow<ZonesUiState> = _uiState.asStateFlow()

    init {
        loadZones()
    }

    fun loadZones() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            try {
                val user = authRepository.fetchCurrentUser()
                val systems = zoneRepository.getMyZoneSystems()
                _uiState.update { it.copy(zoneSystems = systems, user = user, isLoading = false) }
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoading = false, error = e.message ?: "Failed to load zones") }
            }
        }
    }

    fun selectSport(sport: SportType?) {
        _uiState.update { it.copy(selectedSport = sport) }
    }

    fun filteredSystems(): List<ZoneSystem> {
        val state = _uiState.value
        val sport = state.selectedSport ?: return state.zoneSystems
        return state.zoneSystems.filter { it.sportType == sport }
    }
}
