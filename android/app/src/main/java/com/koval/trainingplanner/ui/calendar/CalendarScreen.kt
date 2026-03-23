package com.koval.trainingplanner.ui.calendar

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.CalendarViewDay
import androidx.compose.material.icons.filled.CalendarViewWeek
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.koval.trainingplanner.domain.model.ScheduleStatus
import com.koval.trainingplanner.ui.calendar.components.ClubSessionCard
import com.koval.trainingplanner.ui.calendar.components.DayStrip
import com.koval.trainingplanner.ui.calendar.components.GoalCard
import com.koval.trainingplanner.ui.calendar.components.WorkoutCard
import com.koval.trainingplanner.ui.theme.Primary
import com.koval.trainingplanner.ui.theme.Success
import com.koval.trainingplanner.ui.theme.SurfaceElevated
import com.koval.trainingplanner.ui.theme.TextMuted
import com.koval.trainingplanner.ui.theme.TextPrimary
import com.koval.trainingplanner.ui.theme.TextSecondary
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.TemporalAdjusters
import java.time.DayOfWeek

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CalendarScreen(
    onTrainingClick: (trainingId: String) -> Unit = {},
    viewModel: CalendarViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsState()
    val today = LocalDate.now()

    PullToRefreshBox(
        isRefreshing = state.isRefreshing,
        onRefresh = { viewModel.loadSchedule(isRefresh = true) },
        modifier = Modifier.fillMaxSize(),
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // Header
            item {
                Spacer(Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = if (state.isWeekView) "Week" else "Today",
                            fontSize = 26.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary,
                        )
                        Text(
                            text = today.format(DateTimeFormatter.ofPattern("EEEE, MMMM d")),
                            fontSize = 13.sp,
                            color = TextSecondary,
                        )
                    }
                    IconButton(onClick = { viewModel.toggleViewMode() }) {
                        Icon(
                            imageVector = if (state.isWeekView) Icons.Filled.CalendarViewDay else Icons.Filled.CalendarViewWeek,
                            contentDescription = "Toggle view",
                            tint = Primary,
                        )
                    }
                }
            }

            // Week view extras
            if (state.isWeekView) {
                // Week navigator
                item {
                    val weekEnd = state.weekStart.plusDays(6)
                    val isThisWeek = state.weekStart == today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        IconButton(onClick = { viewModel.navigateWeek(-1) }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, "Previous week", tint = TextSecondary)
                        }
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = "${state.weekStart.format(DateTimeFormatter.ofPattern("MMM d"))} – ${weekEnd.format(DateTimeFormatter.ofPattern("MMM d, yyyy"))}",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Medium,
                                color = TextPrimary,
                            )
                            if (isThisWeek) {
                                Text("This week", fontSize = 11.sp, color = Primary, fontWeight = FontWeight.SemiBold)
                            }
                        }
                        IconButton(onClick = { viewModel.navigateWeek(1) }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowForward, "Next week", tint = TextSecondary)
                        }
                    }
                }

                // Day strip
                item {
                    DayStrip(
                        weekStart = state.weekStart,
                        selectedDay = state.selectedDay,
                        workouts = state.workouts,
                        clubSessions = state.clubSessions,
                        goals = state.goals,
                        onDayClick = { viewModel.selectDay(it) },
                    )
                }

                // Summary bar
                item {
                    val filtered = viewModel.filteredWorkouts()
                    val completed = filtered.count { it.status == ScheduleStatus.COMPLETED }
                    val label = if (state.selectedDay != null) "workouts on this day" else "workouts this week"
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text("${filtered.size} $label", color = TextSecondary, fontSize = 13.sp)
                        if (completed > 0) {
                            Text("$completed completed", color = Success, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                        }
                    }
                }
            }

            // Loading
            if (state.isLoading) {
                item {
                    Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = Primary)
                    }
                }
            }

            // Workout list
            val displayWorkouts = if (state.isWeekView) {
                viewModel.filteredWorkouts()
            } else {
                viewModel.todayWorkouts()
            }

            // Today summary card (today mode only)
            if (!state.isWeekView && displayWorkouts.isNotEmpty() && !state.isLoading) {
                item {
                    val totalDuration = displayWorkouts.mapNotNull { it.totalDurationSeconds }.sum()
                    val totalTss = displayWorkouts.mapNotNull { it.tss }.sum()
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = SurfaceElevated,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            horizontalArrangement = Arrangement.SpaceEvenly,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            MetricItem("Workouts", "${displayWorkouts.size}")
                            if (totalDuration > 0) {
                                MetricItem("Duration", com.koval.trainingplanner.ui.calendar.components.formatDuration(totalDuration))
                            }
                            if (totalTss > 0) {
                                MetricItem("TSS", "${totalTss.toInt()}")
                            }
                        }
                    }
                }
            }

            if (!state.isLoading) {
                items(displayWorkouts, key = { it.id }) { workout ->
                    WorkoutCard(
                        workout = workout,
                        onClick = { workout.trainingId?.let { onTrainingClick(it) } },
                        onComplete = { viewModel.markCompleted(workout.id) },
                        onSkip = { viewModel.markSkipped(workout.id) },
                        onDelete = { viewModel.deleteWorkout(workout.id) },
                        showDate = state.isWeekView,
                    )
                }

                // Club sessions (filtered by day)
                val displaySessions = if (state.isWeekView) {
                    viewModel.filteredClubSessions()
                } else {
                    viewModel.todayClubSessions()
                }
                if (displaySessions.isNotEmpty()) {
                    item {
                        Text(
                            "Club Sessions",
                            color = TextSecondary,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 2.sp,
                            modifier = Modifier.padding(top = 8.dp),
                        )
                    }
                    items(displaySessions, key = { it.id }) { session ->
                        ClubSessionCard(
                            session = session,
                            currentUserId = state.currentUserId,
                            onJoin = { viewModel.joinSession(session) },
                            onLeave = { viewModel.leaveSession(session) },
                            onTrainingClick = onTrainingClick,
                        )
                    }
                }

                // Goals
                val displayGoals = viewModel.filteredGoals()
                if (displayGoals.isNotEmpty()) {
                    item {
                        Text(
                            "Upcoming Goals",
                            color = TextSecondary,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 2.sp,
                            modifier = Modifier.padding(top = 8.dp),
                        )
                    }
                    items(displayGoals, key = { it.id }) { goal ->
                        GoalCard(goal = goal)
                    }
                }

                // Empty state
                if (displayWorkouts.isEmpty() && displaySessions.isEmpty() && displayGoals.isEmpty()) {
                    item {
                        Column(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 48.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Icon(
                                imageVector = Icons.Filled.CalendarMonth,
                                contentDescription = null,
                                tint = TextMuted,
                                modifier = Modifier.size(48.dp),
                            )
                            Spacer(Modifier.height(16.dp))
                            Text(
                                text = if (state.isWeekView) "No workouts this week" else "No training scheduled for today",
                                color = TextSecondary,
                                fontSize = 15.sp,
                                textAlign = TextAlign.Center,
                            )
                            if (!state.isWeekView) {
                                Spacer(Modifier.height(12.dp))
                                TextButton(onClick = { viewModel.toggleViewMode() }) {
                                    Text("View full week", color = Primary)
                                }
                            }
                        }
                    }
                }

                item { Spacer(Modifier.height(16.dp)) }
            }
        }
    }
}

@Composable
private fun MetricItem(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, color = TextPrimary, fontSize = 20.sp, fontWeight = FontWeight.Bold)
        Text(label, color = TextSecondary, fontSize = 11.sp)
    }
}
