package com.koval.trainingplanner.ui.calendar.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import com.koval.trainingplanner.ui.theme.Success
import com.koval.trainingplanner.ui.theme.Surface as SurfaceColor
import com.koval.trainingplanner.ui.theme.TextPrimary
import com.koval.trainingplanner.ui.theme.TextSecondary
import com.koval.trainingplanner.ui.theme.Warning

@Composable
fun WorkoutCard(
    workout: ScheduledWorkout,
    onClick: () -> Unit = {},
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
        shape = RoundedCornerShape(10.dp),
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
                    .width(3.dp)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(topStart = 10.dp, bottomStart = 10.dp))
                    .background(statusColor),
            )

            Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp).weight(1f)) {
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
                        modifier = Modifier.padding(bottom = 4.dp),
                    )
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    SportIcon(sport = workout.sportType)

                    Spacer(Modifier.width(10.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = workout.trainingTitle ?: "Workout",
                            color = TextPrimary,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium,
                            maxLines = 1,
                        )
                        val metricParts = buildList {
                            workout.totalDurationSeconds?.let { add(formatDuration(it)) }
                            workout.tss?.let { add("${it.toInt()} TSS") }
                            workout.intensityFactor?.let { add("IF %.2f".format(it)) }
                        }
                        if (metricParts.isNotEmpty()) {
                            Text(
                                text = metricParts.joinToString(" · "),
                                color = TextSecondary,
                                fontSize = 12.sp,
                            )
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
                        fontSize = 12.sp,
                        fontStyle = FontStyle.Italic,
                        modifier = Modifier.padding(start = 46.dp, top = 2.dp),
                        maxLines = 1,
                    )
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
