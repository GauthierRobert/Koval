package com.koval.trainingplanner.ui.calendar.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.koval.trainingplanner.domain.model.ClubTrainingSession
import com.koval.trainingplanner.domain.model.RaceGoal
import com.koval.trainingplanner.domain.model.ScheduleStatus
import com.koval.trainingplanner.domain.model.ScheduledWorkout
import com.koval.trainingplanner.ui.theme.Danger
import com.koval.trainingplanner.ui.theme.Primary
import com.koval.trainingplanner.ui.theme.SportCycling
import com.koval.trainingplanner.ui.theme.Success
import com.koval.trainingplanner.ui.theme.TextMuted
import com.koval.trainingplanner.ui.theme.TextPrimary
import com.koval.trainingplanner.ui.theme.TextSecondary
import java.time.LocalDate
import java.time.ZonedDateTime

@Composable
fun DayStrip(
    weekStart: LocalDate,
    selectedDay: LocalDate?,
    workouts: List<ScheduledWorkout>,
    clubSessions: List<ClubTrainingSession> = emptyList(),
    goals: List<RaceGoal> = emptyList(),
    onDayClick: (LocalDate) -> Unit,
    modifier: Modifier = Modifier,
) {
    val today = LocalDate.now()
    val dayLabels = listOf("M", "T", "W", "T", "F", "S", "S")

    Row(
        modifier = modifier.fillMaxWidth().padding(horizontal = 8.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
    ) {
        for (i in 0..6) {
            val day = weekStart.plusDays(i.toLong())
            val isToday = day == today
            val isSelected = day == selectedDay
            val dateStr = day.toString()
            val dayWorkouts = workouts.filter { it.scheduledDate == dateStr }
            val allDone = dayWorkouts.isNotEmpty() && dayWorkouts.all { it.status == ScheduleStatus.COMPLETED }
            val daySessions = clubSessions.filter { session ->
                try {
                    ZonedDateTime.parse(session.scheduledAt).toLocalDate() == day
                } catch (_: Exception) {
                    try { LocalDate.parse(session.scheduledAt.take(10)) == day } catch (_: Exception) { false }
                }
            }
            val hasGoal = goals.any { it.raceDate == dateStr }

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .clip(CircleShape)
                    .clickable { onDayClick(day) }
                    .padding(horizontal = 4.dp, vertical = 4.dp),
            ) {
                Text(
                    text = dayLabels[i],
                    fontSize = 11.sp,
                    color = TextMuted,
                    fontWeight = FontWeight.Medium,
                )

                Spacer(Modifier.height(4.dp))

                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .then(
                            when {
                                isToday -> Modifier.background(Primary)
                                isSelected -> Modifier.border(1.5.dp, Primary, CircleShape)
                                else -> Modifier
                            }
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = day.dayOfMonth.toString(),
                        fontSize = 14.sp,
                        fontWeight = if (isToday || isSelected) FontWeight.Bold else FontWeight.Normal,
                        color = when {
                            isToday -> TextPrimary
                            isSelected -> Primary
                            else -> TextSecondary
                        },
                    )
                }

                Spacer(Modifier.height(4.dp))

                // Activity dots
                Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                    val workoutDots = dayWorkouts.size.coerceAtMost(3)
                    repeat(workoutDots) {
                        Box(
                            modifier = Modifier
                                .size(5.dp)
                                .clip(CircleShape)
                                .background(if (allDone) Success else Primary),
                        )
                    }
                    val sessionDots = daySessions.size.coerceAtMost(2)
                    repeat(sessionDots) {
                        Box(
                            modifier = Modifier
                                .size(5.dp)
                                .clip(CircleShape)
                                .background(SportCycling),
                        )
                    }
                    if (hasGoal) {
                        Box(
                            modifier = Modifier
                                .size(5.dp)
                                .clip(CircleShape)
                                .background(Danger),
                        )
                    }
                }
            }
        }
    }
}
