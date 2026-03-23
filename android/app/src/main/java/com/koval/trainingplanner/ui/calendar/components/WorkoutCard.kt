package com.koval.trainingplanner.ui.calendar.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.koval.trainingplanner.domain.model.ScheduleStatus
import com.koval.trainingplanner.domain.model.ScheduledWorkout
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import com.koval.trainingplanner.ui.theme.Border
import com.koval.trainingplanner.ui.theme.Danger
import com.koval.trainingplanner.ui.theme.Success
import com.koval.trainingplanner.ui.theme.Surface as SurfaceColor
import com.koval.trainingplanner.ui.theme.TextPrimary
import com.koval.trainingplanner.ui.theme.TextSecondary
import com.koval.trainingplanner.ui.theme.Warning

@Composable
fun WorkoutCard(
    workout: ScheduledWorkout,
    onClick: () -> Unit = {},
    onComplete: () -> Unit,
    onSkip: () -> Unit,
    onDelete: () -> Unit,
    showDate: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val statusColor = when (workout.status) {
        ScheduleStatus.PENDING -> TextSecondary
        ScheduleStatus.COMPLETED -> Success
        ScheduleStatus.SKIPPED -> Warning
    }

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(14.dp),
        color = SurfaceColor,
        border = androidx.compose.foundation.BorderStroke(1.dp, Border),
    ) {
        Row(
            modifier = Modifier
                .height(IntrinsicSize.Min)
                .fillMaxWidth(),
        ) {
            // Left color bar
            Box(
                modifier = Modifier
                    .width(4.dp)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(topStart = 14.dp, bottomStart = 14.dp))
                    .background(statusColor),
            )

            Column(modifier = Modifier.padding(12.dp).weight(1f)) {
                if (showDate) {
                    val dateLabel = try {
                        LocalDate.parse(workout.scheduledDate)
                            .format(DateTimeFormatter.ofPattern("EEE, MMM d"))
                    } catch (_: Exception) {
                        workout.scheduledDate
                    }
                    Text(
                        text = dateLabel,
                        color = TextSecondary,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.padding(bottom = 6.dp),
                    )
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    SportIcon(sport = workout.sportType)

                    Spacer(Modifier.width(12.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = workout.trainingTitle ?: "Workout",
                            color = TextPrimary,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Medium,
                            maxLines = 1,
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            workout.totalDurationSeconds?.let {
                                Text(formatDuration(it), color = TextSecondary, fontSize = 13.sp)
                            }
                            workout.tss?.let {
                                Text("${it.toInt()} TSS", color = TextSecondary, fontSize = 13.sp)
                            }
                            workout.intensityFactor?.let {
                                Text("IF %.2f".format(it), color = TextSecondary, fontSize = 13.sp)
                            }
                        }
                    }

                    // Status pill
                    StatusPill(workout.status)
                }

                // Notes
                workout.notes?.takeIf { it.isNotBlank() }?.let {
                    Text(
                        text = it,
                        color = TextSecondary,
                        fontSize = 13.sp,
                        fontStyle = FontStyle.Italic,
                        modifier = Modifier.padding(start = 52.dp, top = 4.dp),
                    )
                }

                // Actions for pending
                if (workout.status == ScheduleStatus.PENDING) {
                    Spacer(Modifier.height(8.dp))
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        TextButton(onClick = onComplete) {
                            Icon(Icons.Filled.Check, null, tint = Success, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Complete", color = Success, fontSize = 13.sp)
                        }
                        TextButton(onClick = onSkip) {
                            Icon(Icons.Filled.SkipNext, null, tint = Warning, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Skip", color = Warning, fontSize = 13.sp)
                        }
                        Spacer(Modifier.weight(1f))
                        IconButton(onClick = onDelete, modifier = Modifier.size(32.dp)) {
                            Icon(Icons.Filled.Delete, null, tint = Danger, modifier = Modifier.size(16.dp))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StatusPill(status: ScheduleStatus) {
    val (color, label) = when (status) {
        ScheduleStatus.PENDING -> TextSecondary to "Pending"
        ScheduleStatus.COMPLETED -> Success to "Done"
        ScheduleStatus.SKIPPED -> Warning to "Skipped"
    }
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = color.copy(alpha = 0.12f),
    ) {
        Text(
            text = label,
            color = color,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
        )
    }
}

fun formatDuration(seconds: Int): String {
    val h = seconds / 3600
    val m = (seconds % 3600) / 60
    return if (h > 0) "${h}h ${m}m" else "${m}m"
}
