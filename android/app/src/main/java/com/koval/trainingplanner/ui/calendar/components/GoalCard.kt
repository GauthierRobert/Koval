package com.koval.trainingplanner.ui.calendar.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.koval.trainingplanner.domain.model.RaceGoal
import com.koval.trainingplanner.domain.model.SportType
import com.koval.trainingplanner.ui.theme.Border
import com.koval.trainingplanner.ui.theme.Danger
import com.koval.trainingplanner.ui.theme.Primary
import com.koval.trainingplanner.ui.theme.Surface as SurfaceColor
import com.koval.trainingplanner.ui.theme.TextPrimary
import com.koval.trainingplanner.ui.theme.TextSecondary
import com.koval.trainingplanner.ui.theme.Warning
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

@Composable
fun GoalCard(
    goal: RaceGoal,
    modifier: Modifier = Modifier,
) {
    val priorityColor = when (goal.priority) {
        "A" -> Danger
        "B" -> Warning
        else -> TextSecondary
    }

    val sport = try { SportType.valueOf(goal.sport) } catch (_: Exception) { null }
    val daysAway = try {
        ChronoUnit.DAYS.between(LocalDate.now(), LocalDate.parse(goal.raceDate))
    } catch (_: Exception) { null }

    val dateLabel = try {
        LocalDate.parse(goal.raceDate).format(DateTimeFormatter.ofPattern("EEE, MMM d yyyy"))
    } catch (_: Exception) { goal.raceDate }

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        color = SurfaceColor,
        border = BorderStroke(1.dp, Border),
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth(),
            ) {
                // Trophy icon
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .background(priorityColor.copy(alpha = 0.12f), RoundedCornerShape(12.dp)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.Filled.EmojiEvents,
                        contentDescription = null,
                        tint = priorityColor,
                        modifier = Modifier.size(22.dp),
                    )
                }

                Spacer(Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = goal.title,
                        color = TextPrimary,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(dateLabel, color = TextSecondary, fontSize = 12.sp)
                        goal.distance?.let {
                            Text(it, color = TextSecondary, fontSize = 12.sp)
                        }
                    }
                }

                // Priority badge
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = priorityColor.copy(alpha = 0.12f),
                ) {
                    Text(
                        text = goal.priority,
                        color = priorityColor,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    )
                }
            }

            Spacer(Modifier.height(8.dp))

            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.padding(start = 52.dp),
            ) {
                // Sport icon
                if (sport != null) {
                    SportIcon(sport = sport, size = 20.dp, iconSize = 12.dp)
                }

                // Countdown
                daysAway?.let { days ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.Schedule, null, tint = TextSecondary, modifier = Modifier.size(14.dp))
                        Spacer(Modifier.width(4.dp))
                        val countdownText = when {
                            days == 0L -> "Today!"
                            days == 1L -> "Tomorrow"
                            days > 0 -> "$days days away"
                            else -> "${-days} days ago"
                        }
                        val countdownColor = when {
                            days <= 7 -> Primary
                            else -> TextSecondary
                        }
                        Text(countdownText, color = countdownColor, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                    }
                }

                // Location
                goal.location?.takeIf { it.isNotBlank() }?.let {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.LocationOn, null, tint = TextSecondary, modifier = Modifier.size(14.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(it, color = TextSecondary, fontSize = 12.sp, maxLines = 1)
                    }
                }
            }

            // Target time
            goal.targetTime?.takeIf { it.isNotBlank() }?.let {
                Text(
                    text = "Target: $it",
                    color = Primary,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.padding(start = 52.dp, top = 4.dp),
                )
            }
        }
    }
}
