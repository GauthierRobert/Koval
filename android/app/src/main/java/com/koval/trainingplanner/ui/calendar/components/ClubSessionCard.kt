package com.koval.trainingplanner.ui.calendar.components

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.HowToReg
import androidx.compose.material.icons.filled.HourglassTop
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.koval.trainingplanner.domain.model.ClubTrainingSession
import com.koval.trainingplanner.ui.theme.Border
import com.koval.trainingplanner.ui.theme.Danger
import com.koval.trainingplanner.ui.theme.Primary
import com.koval.trainingplanner.ui.theme.Session
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
        shape = RoundedCornerShape(10.dp),
        color = SurfaceColor,
        border = BorderStroke(1.dp, if (isCancelled) TextMuted else Border),
    ) {
        Row(
            modifier = Modifier
                .height(IntrinsicSize.Min)
                .fillMaxWidth(),
        ) {
            // Left accent bar
            Box(
                modifier = Modifier
                    .width(3.dp)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(topStart = 10.dp, bottomStart = 10.dp))
                    .background(if (isCancelled) TextMuted else Session),
            )

            Column(
                modifier = Modifier
                    .padding(horizontal = 10.dp, vertical = 8.dp)
                    .weight(1f),
            ) {
                // Row 1: Sport icon + participation indicator, title, club name, action
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    // Sport icon with participation indicator below
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        SportIcon(sport = session.sport)
                        if (isParticipant) {
                            Icon(
                                Icons.Filled.Check,
                                contentDescription = "Participating",
                                tint = Success,
                                modifier = Modifier.size(12.dp),
                            )
                        } else if (isOnWaitingList) {
                            Icon(
                                Icons.Filled.HourglassTop,
                                contentDescription = "On waiting list",
                                tint = Warning,
                                modifier = Modifier.size(12.dp),
                            )
                        }
                    }

                    Spacer(Modifier.width(10.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = session.title,
                            color = if (isCancelled) TextMuted else TextPrimary,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium,
                            textDecoration = if (isCancelled) TextDecoration.LineThrough else null,
                            maxLines = 1,
                        )

                        // Compact meta line: club · time · duration · participants
                        val metaParts = buildList {
                            session.clubName?.let { add(it) }
                            try {
                                val time = ZonedDateTime.parse(session.scheduledAt)
                                    .format(DateTimeFormatter.ofPattern("HH:mm"))
                                add(time)
                            } catch (_: Exception) {}
                            session.durationMinutes?.let { add("${it}min") }
                            val count = session.participantIds.size
                            val max = session.maxParticipants
                            if (max != null) add("$count/$max") else add("$count")
                        }
                        Text(
                            text = metaParts.joinToString(" · "),
                            color = TextSecondary,
                            fontSize = 12.sp,
                            maxLines = 1,
                        )
                    }

                    // Action: Leave if joined, Join if not
                    if (!isCancelled && currentUserId != null) {
                        if (isJoined) {
                            IconButton(
                                onClick = onLeave,
                                modifier = Modifier.size(32.dp),
                            ) {
                                Icon(
                                    Icons.AutoMirrored.Filled.ExitToApp,
                                    contentDescription = if (isOnWaitingList) "Leave waiting list" else "Leave",
                                    tint = Danger,
                                    modifier = Modifier.size(18.dp),
                                )
                            }
                        } else {
                            TextButton(onClick = onJoin) {
                                Text(
                                    text = if (isFull) "Wait" else "Join",
                                    color = if (isFull) Warning else Success,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold,
                                )
                            }
                        }
                    }
                }

                // Location (only if present)
                session.location?.takeIf { it.isNotBlank() }?.let {
                    Text(
                        text = it,
                        color = TextMuted,
                        fontSize = 11.sp,
                        modifier = Modifier.padding(start = 46.dp),
                        maxLines = 1,
                    )
                }

                // Waiting list info
                if (session.waitingList.isNotEmpty() && !isOnWaitingList) {
                    Text(
                        text = "${session.waitingList.size} on waiting list",
                        color = Warning,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.padding(start = 46.dp),
                    )
                } else if (isOnWaitingList) {
                    val position = session.waitingList.indexOf(currentUserId) + 1
                    Text(
                        text = "Position $position on waiting list",
                        color = Warning,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.padding(start = 46.dp),
                    )
                }

                // Cancellation reason
                if (isCancelled) {
                    Text(
                        text = "Cancelled" + (session.cancellationReason?.let { " — $it" } ?: ""),
                        color = TextMuted,
                        fontSize = 11.sp,
                        modifier = Modifier.padding(start = 46.dp),
                    )
                }

                // Linked trainings with clickable titles
                if (session.linkedTrainings.isNotEmpty()) {
                    Row(
                        modifier = Modifier.padding(start = 46.dp, top = 2.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        session.linkedTrainings.forEach { linked ->
                            linked.title?.let { title ->
                                Surface(
                                    shape = RoundedCornerShape(4.dp),
                                    color = Primary.copy(alpha = 0.1f),
                                    modifier = Modifier.clickable { onTrainingClick(linked.trainingId) },
                                ) {
                                    Text(
                                        text = title,
                                        color = Primary,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Medium,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                        maxLines = 1,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
