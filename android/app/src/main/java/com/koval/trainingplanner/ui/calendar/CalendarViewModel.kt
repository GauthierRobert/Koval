package com.koval.trainingplanner.ui.calendar

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.koval.trainingplanner.data.repository.AuthRepository
import com.koval.trainingplanner.data.repository.CalendarRepository
import com.koval.trainingplanner.data.repository.GoalRepository
import com.koval.trainingplanner.domain.model.ClubTrainingSession
import com.koval.trainingplanner.domain.model.RaceGoal
import com.koval.trainingplanner.domain.model.ScheduleStatus
import com.koval.trainingplanner.domain.model.ScheduledWorkout
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.temporal.TemporalAdjusters
import javax.inject.Inject

data class CalendarUiState(
    val workouts: List<ScheduledWorkout> = emptyList(),
    val clubSessions: List<ClubTrainingSession> = emptyList(),
    val goals: List<RaceGoal> = emptyList(),
    val currentUserId: String? = null,
    val weekStart: LocalDate = LocalDate.now().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)),
    val selectedDay: LocalDate? = null,
    val isWeekView: Boolean = false,
    val isLoading: Boolean = false,
    val isRefreshing: Boolean = false,
    val error: String? = null,
)

@HiltViewModel
class CalendarViewModel @Inject constructor(
    private val calendarRepository: CalendarRepository,
    private val goalRepository: GoalRepository,
    private val authRepository: AuthRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(CalendarUiState())
    val uiState: StateFlow<CalendarUiState> = _uiState.asStateFlow()

    private val dateFormat = DateTimeFormatter.ISO_LOCAL_DATE

    init {
        loadCurrentUser()
        loadSchedule()
    }

    private fun loadCurrentUser() {
        viewModelScope.launch {
            try {
                val user = authRepository.fetchCurrentUser()
                _uiState.update { it.copy(currentUserId = user.id) }
            } catch (_: Exception) {}
        }
    }

    fun loadSchedule(isRefresh: Boolean = false) {
        viewModelScope.launch {
            val state = _uiState.value
            _uiState.update {
                it.copy(
                    isLoading = !isRefresh,
                    isRefreshing = isRefresh,
                    error = null,
                )
            }
            try {
                val start = state.weekStart.format(dateFormat)
                val end = state.weekStart.plusDays(6).format(dateFormat)
                val workouts = calendarRepository.getSchedule(start, end)
                val sessions = calendarRepository.getClubSessions(start, end)
                val goals = try { goalRepository.getGoals() } catch (_: Exception) { emptyList() }
                _uiState.update {
                    it.copy(
                        workouts = workouts,
                        clubSessions = sessions,
                        goals = goals,
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

    fun toggleViewMode() {
        _uiState.update { it.copy(isWeekView = !it.isWeekView, selectedDay = null) }
    }

    fun selectDay(day: LocalDate) {
        _uiState.update {
            it.copy(selectedDay = if (it.selectedDay == day) null else day)
        }
    }

    fun navigateWeek(delta: Int) {
        _uiState.update { it.copy(weekStart = it.weekStart.plusWeeks(delta.toLong()), selectedDay = null) }
        loadSchedule()
    }

    fun goToThisWeek() {
        val today = LocalDate.now()
        _uiState.update {
            it.copy(
                weekStart = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)),
                selectedDay = null,
            )
        }
        loadSchedule()
    }

    fun markCompleted(id: String) {
        viewModelScope.launch {
            try {
                calendarRepository.markCompleted(id)
                updateWorkoutStatus(id, ScheduleStatus.COMPLETED)
            } catch (_: Exception) {}
        }
    }

    fun markSkipped(id: String) {
        viewModelScope.launch {
            try {
                calendarRepository.markSkipped(id)
                updateWorkoutStatus(id, ScheduleStatus.SKIPPED)
            } catch (_: Exception) {}
        }
    }

    fun deleteWorkout(id: String) {
        viewModelScope.launch {
            try {
                calendarRepository.deleteScheduledWorkout(id)
                _uiState.update { state ->
                    state.copy(workouts = state.workouts.filter { it.id != id })
                }
            } catch (_: Exception) {}
        }
    }

    private fun updateWorkoutStatus(id: String, status: ScheduleStatus) {
        _uiState.update { state ->
            state.copy(
                workouts = state.workouts.map {
                    if (it.id == id) it.copy(status = status) else it
                }
            )
        }
    }

    // Helpers for UI
    fun workoutsForDay(day: LocalDate): List<ScheduledWorkout> {
        val dateStr = day.format(dateFormat)
        return _uiState.value.workouts.filter { it.scheduledDate == dateStr }
    }

    fun todayWorkouts(): List<ScheduledWorkout> = workoutsForDay(LocalDate.now())

    fun filteredWorkouts(): List<ScheduledWorkout> {
        val state = _uiState.value
        return if (state.selectedDay != null) {
            workoutsForDay(state.selectedDay)
        } else {
            state.workouts.sortedBy { it.scheduledDate }
        }
    }

    // Join/leave club session
    fun joinSession(session: ClubTrainingSession) {
        viewModelScope.launch {
            // Optimistic update
            _uiState.update { state ->
                state.copy(clubSessions = state.clubSessions.map {
                    if (it.id == session.id) {
                        val isFull = it.maxParticipants != null && it.participantIds.size >= it.maxParticipants
                        if (isFull) {
                            it.copy(onWaitingList = true, waitingListPosition = 0)
                        } else {
                            it.copy(joined = true)
                        }
                    } else it
                })
            }
            try {
                calendarRepository.joinSession(session.clubId, session.id)
                // Reload to get accurate server state
                loadSchedule(isRefresh = true)
            } catch (_: Exception) {
                // Revert on failure
                _uiState.update { state ->
                    state.copy(clubSessions = state.clubSessions.map {
                        if (it.id == session.id) {
                            it.copy(joined = false, onWaitingList = false, waitingListPosition = 0)
                        } else it
                    })
                }
            }
        }
    }

    fun leaveSession(session: ClubTrainingSession) {
        viewModelScope.launch {
            val prevJoined = session.joined
            val prevOnWaitingList = session.onWaitingList
            val prevWaitingListPosition = session.waitingListPosition
            // Optimistic update
            _uiState.update { state ->
                state.copy(clubSessions = state.clubSessions.map {
                    if (it.id == session.id) {
                        it.copy(joined = false, onWaitingList = false, waitingListPosition = 0)
                    } else it
                })
            }
            try {
                calendarRepository.leaveSession(session.clubId, session.id)
                loadSchedule(isRefresh = true)
            } catch (_: Exception) {
                // Revert on failure
                _uiState.update { state ->
                    state.copy(clubSessions = state.clubSessions.map {
                        if (it.id == session.id) {
                            it.copy(
                                joined = prevJoined,
                                onWaitingList = prevOnWaitingList,
                                waitingListPosition = prevWaitingListPosition,
                            )
                        } else it
                    })
                }
            }
        }
    }

    // Club session filtering
    private fun sessionDate(session: ClubTrainingSession): LocalDate? {
        return try {
            ZonedDateTime.parse(session.scheduledAt).toLocalDate()
        } catch (_: Exception) {
            try {
                LocalDate.parse(session.scheduledAt.take(10))
            } catch (_: Exception) {
                null
            }
        }
    }

    fun clubSessionsForDay(day: LocalDate): List<ClubTrainingSession> {
        return _uiState.value.clubSessions.filter { sessionDate(it) == day }
    }

    fun todayClubSessions(): List<ClubTrainingSession> = clubSessionsForDay(LocalDate.now())

    fun filteredClubSessions(): List<ClubTrainingSession> {
        val state = _uiState.value
        return if (state.selectedDay != null) {
            clubSessionsForDay(state.selectedDay)
        } else {
            state.clubSessions
        }
    }

    // Goal filtering
    fun goalsForDay(day: LocalDate): List<RaceGoal> {
        val dateStr = day.format(dateFormat)
        return _uiState.value.goals.filter { it.raceDate == dateStr }
    }

    fun filteredGoals(): List<RaceGoal> {
        val state = _uiState.value
        val today = LocalDate.now()
        return if (state.isWeekView) {
            if (state.selectedDay != null) {
                goalsForDay(state.selectedDay)
            } else {
                // Goals within the displayed week or upcoming
                val weekEnd = state.weekStart.plusDays(6)
                state.goals.filter {
                    try {
                        val d = LocalDate.parse(it.raceDate)
                        d in state.weekStart..weekEnd || d.isAfter(today)
                    } catch (_: Exception) { false }
                }.sortedBy { it.raceDate }
            }
        } else {
            // Today view: show upcoming goals (from today onward)
            state.goals.filter {
                try {
                    !LocalDate.parse(it.raceDate).isBefore(today)
                } catch (_: Exception) { false }
            }.sortedBy { it.raceDate }
        }
    }
}
