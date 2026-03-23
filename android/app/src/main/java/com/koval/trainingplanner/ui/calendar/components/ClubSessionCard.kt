package com.koval.trainingplanner.ui.calendar.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.HowToReg
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.koval.trainingplanner.domain.model.ClubTrainingSession
import com.koval.trainingplanner.ui.theme.Border
import com.koval.trainingplanner.ui.theme.Danger
import com.koval.trainingplanner.ui.theme.Primary
import com.koval.trainingplanner.ui.theme.Success
import com.koval.trainingplanner.ui.theme.Surface as SurfaceColor
import com.koval.trainingplanner.ui.theme.TextMuted
import com.koval.trainingplanner.ui.theme.TextPrimary
import com.koval.trainingplanner.ui.theme.TextSecondary
import com.koval.trainingplanner.ui.theme.Warning
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

@Composable
fun ClubSessionCard(
    session: ClubTrainingSession,
    currentUserId: String? = null,
    onJoin: () -> Unit = {},
    onLeave: () -> Unit = {},
    onTrainingClick: (trainingId: String) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val isCancelled = session.cancelled
    val isParticipant = currentUserId != null && currentUserId in session.participantIds
    val isOnWaitingList = currentUserId != null && currentUserId in session.waitingList
    val isJoined = isParticipant || isOnWaitingList
    val isFull = session.maxParticipants != null && session.participantIds.size >= session.maxParticipants

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        color = SurfaceColor,
        border = BorderStroke(1.dp, if (isCancelled) TextMuted else if (isParticipant) Success.copy(alpha = 0.4f) else Border),
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth(),
            ) {
                SportIcon(sport = session.sport)
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = session.title,
                        color = if (isCancelled) TextMuted else TextPrimary,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Medium,
                        textDecoration = if (isCancelled) TextDecoration.LineThrough else null,
                        maxLines = 1,
                    )
                    session.clubName?.let {
                        Text(text = it, color = Primary, fontSize = 11.sp, fontWeight = FontWeight.Medium)
                    }
                }

                // Joined status pill
                if (isParticipant) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = Success.copy(alpha = 0.12f),
                    ) {
                        Text(
                            "Joined",
                            color = Success,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        )
                    }
                } else if (isOnWaitingList) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = Warning.copy(alpha = 0.12f),
                    ) {
                        Text(
                            "Waiting",
                            color = Warning,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        )
                    }
                }
            }

            Spacer(Modifier.height(8.dp))

            // Time + duration
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.padding(start = 52.dp),
            ) {
                val timeStr = try {
                    ZonedDateTime.parse(session.scheduledAt)
                        .format(DateTimeFormatter.ofPattern("EEE HH:mm"))
                } catch (_: Exception) { session.scheduledAt }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Schedule, null, tint = TextSecondary, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(timeStr, color = TextSecondary, fontSize = 12.sp)
                }

                session.durationMinutes?.let {
                    Text("${it}min", color = TextSecondary, fontSize = 12.sp)
                }
            }

            session.location?.takeIf { it.isNotBlank() }?.let {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(start = 52.dp, top = 4.dp),
                ) {
                    Icon(Icons.Filled.LocationOn, null, tint = TextSecondary, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(it, color = TextSecondary, fontSize = 12.sp, maxLines = 1)
                }
            }

            // Participants
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(start = 52.dp, top = 4.dp),
            ) {
                Icon(Icons.Filled.Group, null, tint = TextSecondary, modifier = Modifier.size(14.dp))
                Spacer(Modifier.width(4.dp))
                val count = session.participantIds.size
                val max = session.maxParticipants
                val text = if (max != null) "$count/$max" else "$count participants"
                Text(text, color = TextSecondary, fontSize = 12.sp)
                if (isFull && !isJoined) {
                    Spacer(Modifier.width(6.dp))
                    Text("Full", color = Warning, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                }
                if (session.waitingList.isNotEmpty()) {
                    Spacer(Modifier.width(6.dp))
                    Text("(${session.waitingList.size} waiting)", color = TextMuted, fontSize = 11.sp)
                }
            }

            // Cancellation
            if (isCancelled) {
                Text(
                    text = "Cancelled" + (session.cancellationReason?.let { " — $it" } ?: ""),
                    color = TextMuted,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(start = 52.dp, top = 4.dp),
                )
            }

            // Linked trainings
            session.linkedTrainings.forEach { linked ->
                linked.title?.let { title ->
                    Text(
                        text = "Training: $title",
                        color = Primary,
                        fontSize = 12.sp,
                        modifier = Modifier
                            .padding(start = 52.dp, top = 4.dp)
                            .clickable { onTrainingClick(linked.trainingId) },
                    )
                }
            }

            // Join/Leave button
            if (!isCancelled && currentUserId != null) {
                Spacer(Modifier.height(8.dp))
                Row(
                    modifier = Modifier.padding(start = 44.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    if (isJoined) {
                        TextButton(onClick = onLeave) {
                            Icon(
                                Icons.AutoMirrored.Filled.ExitToApp,
                                null,
                                tint = Danger,
                                modifier = Modifier.size(16.dp),
                            )
                            Spacer(Modifier.width(4.dp))
                            Text(
                                if (isOnWaitingList) "Leave waiting list" else "Leave",
                                color = Danger,
                                fontSize = 13.sp,
                            )
                        }
                    } else {
                        TextButton(onClick = onJoin) {
                            Icon(
                                Icons.Filled.HowToReg,
                                null,
                                tint = if (isFull) Warning else Success,
                                modifier = Modifier.size(16.dp),
                            )
                            Spacer(Modifier.width(4.dp))
                            Text(
                                if (isFull) "Join waiting list" else "Join",
                                color = if (isFull) Warning else Success,
                                fontSize = 13.sp,
                            )
                        }
                    }
                }
            }
        }
    }
}
