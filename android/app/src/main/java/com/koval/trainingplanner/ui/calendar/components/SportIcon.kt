package com.koval.trainingplanner.ui.calendar.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DirectionsBike
import androidx.compose.material.icons.filled.DirectionsRun
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.Pool
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.koval.trainingplanner.domain.model.SportType
import com.koval.trainingplanner.ui.theme.SportBrick
import com.koval.trainingplanner.ui.theme.SportCycling
import com.koval.trainingplanner.ui.theme.SportRunning
import com.koval.trainingplanner.ui.theme.SportSwimming
import com.koval.trainingplanner.ui.theme.TextSecondary

@Composable
fun SportIcon(
    sport: SportType?,
    modifier: Modifier = Modifier,
    size: Dp = 40.dp,
    iconSize: Dp = 20.dp,
) {
    val color = when (sport) {
        SportType.CYCLING -> SportCycling
        SportType.RUNNING -> SportRunning
        SportType.SWIMMING -> SportSwimming
        SportType.BRICK -> SportBrick
        null -> TextSecondary
    }
    val icon = when (sport) {
        SportType.CYCLING -> Icons.Filled.DirectionsBike
        SportType.RUNNING -> Icons.Filled.DirectionsRun
        SportType.SWIMMING -> Icons.Filled.Pool
        SportType.BRICK -> Icons.Filled.FitnessCenter
        null -> Icons.Filled.FitnessCenter
    }

    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(color.copy(alpha = 0.12f)),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = sport?.name,
            tint = color,
            modifier = Modifier.size(iconSize),
        )
    }
}
