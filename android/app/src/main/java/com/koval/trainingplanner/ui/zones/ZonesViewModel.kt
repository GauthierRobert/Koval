package com.koval.trainingplanner.ui.zones

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.koval.trainingplanner.data.repository.AuthRepository
import com.koval.trainingplanner.data.repository.ZoneRepository
import com.koval.trainingplanner.domain.model.SportType
import com.koval.trainingplanner.domain.model.User
import com.koval.trainingplanner.domain.model.Zone
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
                val customSystems = zoneRepository.getMyZoneSystems()
                val systems = mergeWithDefaults(customSystems)
                _uiState.update { it.copy(zoneSystems = systems, user = user, isLoading = false) }
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoading = false, error = e.message ?: "Failed to load zones") }
            }
        }
    }

    /** Prepend built-in default zone systems for sports not already covered by backend defaults. */
    private fun mergeWithDefaults(backendSystems: List<ZoneSystem>): List<ZoneSystem> {
        val coveredSports = backendSystems
            .filter { it.defaultForSport }
            .map { it.sportType }
            .toSet()

        val missing = DEFAULT_ZONE_SYSTEMS.filter { it.sportType !in coveredSports }
        // Default systems first, then custom/backend ones
        return missing + backendSystems
    }

    fun selectSport(sport: SportType?) {
        _uiState.update { it.copy(selectedSport = sport) }
    }

    fun filteredSystems(): List<ZoneSystem> {
        val state = _uiState.value
        val sport = state.selectedSport ?: return state.zoneSystems
        return state.zoneSystems.filter { it.sportType == sport }
    }

    companion object {
        private val DEFAULT_ZONE_SYSTEMS = listOf(
            ZoneSystem(
                id = "default-cycling",
                name = "Coggan Power Zones",
                sportType = SportType.CYCLING,
                referenceType = "FTP",
                referenceName = "FTP",
                referenceUnit = "W",
                defaultForSport = true,
                zones = listOf(
                    Zone("Z1", 0, 55, "Active Recovery"),
                    Zone("Z2", 56, 75, "Endurance"),
                    Zone("Z3", 76, 90, "Tempo"),
                    Zone("Z4", 91, 105, "Threshold"),
                    Zone("Z5", 106, 120, "VO2max"),
                    Zone("Z6", 121, 150, "Anaerobic"),
                    Zone("Z7", 151, 300, "Neuromuscular"),
                ),
            ),
            ZoneSystem(
                id = "default-running",
                name = "Running Pace Zones",
                sportType = SportType.RUNNING,
                referenceType = "THRESHOLD_PACE",
                referenceName = "Threshold Pace",
                referenceUnit = "min/km",
                defaultForSport = true,
                zones = listOf(
                    Zone("Z1", 0, 75, "Easy"),
                    Zone("Z2", 76, 85, "Aerobic"),
                    Zone("Z3", 86, 95, "Tempo"),
                    Zone("Z4", 96, 105, "Threshold"),
                    Zone("Z5", 106, 120, "VO2max"),
                ),
            ),
            ZoneSystem(
                id = "default-swimming",
                name = "Swimming Pace Zones",
                sportType = SportType.SWIMMING,
                referenceType = "CSS",
                referenceName = "CSS",
                referenceUnit = "sec/100m",
                defaultForSport = true,
                zones = listOf(
                    Zone("Z1", 0, 80, "Recovery"),
                    Zone("Z2", 81, 90, "Endurance"),
                    Zone("Z3", 91, 100, "Threshold"),
                    Zone("Z4", 101, 110, "VO2max"),
                    Zone("Z5", 111, 130, "Sprint"),
                ),
            ),
        )
    }
}
