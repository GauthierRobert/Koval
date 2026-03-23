package com.koval.trainingplanner.ui.chat.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.koval.trainingplanner.ui.theme.AssistantBubble
import com.koval.trainingplanner.ui.theme.AssistantBubbleBorder
import com.koval.trainingplanner.ui.theme.Primary
import com.koval.trainingplanner.ui.theme.PrimaryMuted
import com.koval.trainingplanner.ui.theme.TextPrimary
import com.koval.trainingplanner.ui.theme.UserBubble
import com.koval.trainingplanner.ui.theme.UserBubbleBright

@Composable
fun MessageBubble(
    role: String,
    content: String,
    isStreaming: Boolean = false,
) {
    val isUser = role == "user"
    val maxWidth = (LocalConfiguration.current.screenWidthDp * 0.82).dp

    Row(
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 3.dp),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start,
        verticalAlignment = Alignment.Bottom,
    ) {
        if (!isUser) {
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(CircleShape)
                    .background(PrimaryMuted),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Filled.AutoAwesome,
                    contentDescription = null,
                    tint = Primary,
                    modifier = Modifier.size(16.dp),
                )
            }
            Spacer(Modifier.width(8.dp))
        }

        Surface(
            shape = RoundedCornerShape(
                topStart = 22.dp, topEnd = 22.dp,
                bottomStart = if (isUser) 22.dp else 4.dp,
                bottomEnd = if (isUser) 4.dp else 22.dp,
            ),
            color = if (isUser) UserBubble else AssistantBubble,
            border = BorderStroke(1.dp, if (isUser) UserBubbleBright else AssistantBubbleBorder),
            modifier = Modifier.widthIn(max = maxWidth),
        ) {
            if (isStreaming && content.isEmpty()) {
                ThinkingDots(Modifier.padding(horizontal = 16.dp, vertical = 12.dp))
            } else {
                val text = if (isStreaming && content.isNotEmpty()) {
                    buildAnnotatedString {
                        append(content)
                        withStyle(SpanStyle(color = Primary)) { append(" \u258C") }
                    }
                } else {
                    buildAnnotatedString { append(content) }
                }
                Text(
                    text = text,
                    color = TextPrimary,
                    fontSize = 15.sp,
                    lineHeight = 23.sp,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                )
            }
        }
    }
}

@Composable
fun ThinkingDots(modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "thinking")
    Row(modifier = modifier, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        repeat(3) { index ->
            val alpha by transition.animateFloat(
                initialValue = 0.3f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(
                    animation = tween(600, delayMillis = index * 200, easing = LinearEasing),
                    repeatMode = RepeatMode.Reverse,
                ),
                label = "dot$index",
            )
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .alpha(alpha)
                    .clip(CircleShape)
                    .background(Primary),
            )
        }
    }
}
