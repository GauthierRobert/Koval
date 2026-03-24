package com.koval.trainingplanner.ui.history

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.koval.trainingplanner.domain.model.BlockSummary
import com.koval.trainingplanner.domain.model.CompletedSession
import com.koval.trainingplanner.ui.calendar.components.SportIcon
import com.koval.trainingplanner.ui.calendar.components.formatDuration
import com.koval.trainingplanner.ui.theme.Background
import com.koval.trainingplanner.ui.theme.Border
import com.koval.trainingplanner.ui.theme.MetricCadence
import com.koval.trainingplanner.ui.theme.MetricHeartRate
import com.koval.trainingplanner.ui.theme.MetricPower
import com.koval.trainingplanner.ui.theme.Primary
import com.koval.trainingplanner.ui.theme.Surface as SurfaceColor
import com.koval.trainingplanner.ui.theme.TextMuted
import com.koval.trainingplanner.ui.theme.TextPrimary
import com.koval.trainingplanner.ui.theme.TextSecondary
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

@Composable
fun SessionDetailScreen(
    onBack: () -> Unit,
    viewModel: SessionDetailViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Background),
    ) {
        // Top bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = TextPrimary)
            }
            Text("Session Details", color = TextPrimary, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
        }

        when {
            state.isLoading -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = Primary)
                }
            }

            state.error != null -> {
                Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
                    Text(state.error ?: "Error", color = TextSecondary)
                }
            }

            state.session != null -> {
                SessionDetailContent(
                    session = state.session!!,
                    onRpeSelect = viewModel::updateRpe,
                )
            }
        }
    }
}

@Composable
private fun SessionDetailContent(
    session: CompletedSession,
    onRpeSelect: (Int) -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // Header card
        item {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                shape = RoundedCornerShape(16.dp),
                color = SurfaceColor,
                border = BorderStroke(1.dp, Border),
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        SportIcon(sport = session.sportType, size = 48.dp, iconSize = 24.dp)
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Text(
                                text = session.title ?: "Session",
                                color = TextPrimary,
                                fontSize = 20.sp,
                                fontWeight = FontWeight.Bold,
                            )
                            session.completedAt?.let { dateStr ->
                                val formatted = try {
                                    ZonedDateTime.parse(dateStr)
                                        .format(DateTimeFormatter.ofPattern("EEEE, MMM d yyyy 'at' HH:mm"))
                                } catch (_: Exception) {
                                    dateStr.take(16)
                                }
                                Text(formatted, color = TextSecondary, fontSize = 13.sp)
                            }
                        }
                    }

                    Spacer(Modifier.height(16.dp))

                    // Metrics grid
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                    ) {
                        session.totalDurationSeconds?.let {
                            MetricCard(Icons.Filled.Timer, formatDuration(it), "Duration", TextSecondary)
                        }
                        session.tss?.let {
                            MetricCard(Icons.Filled.Speed, "${it.toInt()}", "TSS", Primary)
                        }
                        session.intensityFactor?.let {
                            MetricCard(Icons.Filled.FitnessCenter, "%.2f".format(it), "IF", Primary)
                        }
                    }

                    Spacer(Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                    ) {
                        session.avgPower?.let {
                            MetricCard(Icons.Filled.Bolt, "${it.toInt()}W", "Avg Power", MetricPower)
                        }
                        session.avgHR?.let {
                            MetricCard(Icons.Filled.Favorite, "${it.toInt()}", "Avg HR", MetricHeartRate)
                        }
                        session.avgCadence?.let {
                            MetricCard(Icons.Filled.Speed, "${it.toInt()}", "Avg Cadence", MetricCadence)
                        }
                    }
                }
            }
        }

        // RPE selector
        item {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                shape = RoundedCornerShape(16.dp),
                color = SurfaceColor,
                border = BorderStroke(1.dp, Border),
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Rate of Perceived Exertion", color = TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                    ) {
                        (1..10).forEach { rpe ->
                            val isSelected = session.rpe == rpe
                            val color = rpeColor(rpe)
                            Surface(
                                onClick = { onRpeSelect(rpe) },
                                shape = RoundedCornerShape(8.dp),
                                color = if (isSelected) color.copy(alpha = 0.2f) else Color.Transparent,
                                border = BorderStroke(1.dp, if (isSelected) color else Border),
                                modifier = Modifier.size(32.dp),
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Text(
                                        "$rpe",
                                        color = if (isSelected) color else TextMuted,
                                        fontSize = 13.sp,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // Block summaries
        if (session.blockSummaries.isNotEmpty()) {
            item {
                Text(
                    "BLOCK SUMMARIES",
                    color = TextSecondary,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 2.sp,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                )
            }

            itemsIndexed(session.blockSummaries) { index, block ->
                BlockSummaryCard(
                    block = block,
                    index = index + 1,
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
            }
        }

        item { Spacer(Modifier.height(24.dp)) }
    }
}

@Composable
private fun MetricCard(icon: ImageVector, value: String, label: String, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(icon, null, tint = color, modifier = Modifier.size(18.dp))
        Spacer(Modifier.height(4.dp))
        Text(value, color = TextPrimary, fontSize = 16.sp, fontWeight = FontWeight.Bold)
        Text(label, color = TextSecondary, fontSize = 11.sp)
    }
}

@Composable
private fun BlockSummaryCard(block: BlockSummary, index: Int, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = SurfaceColor,
        border = BorderStroke(1.dp, Border),
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Index badge
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = Primary.copy(alpha = 0.12f),
                modifier = Modifier.size(32.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text("$index", color = Primary, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                }
            }

            Spacer(Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = block.label ?: block.type ?: "Block $index",
                    color = TextPrimary,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                )

                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    block.durationSeconds?.let {
                        Text(formatDuration(it), color = TextSecondary, fontSize = 12.sp)
                    }
                    block.actualPower?.let {
                        Text("${it.toInt()}W", color = MetricPower, fontSize = 12.sp)
                    }
                    block.actualHR?.let {
                        Text("${it.toInt()}bpm", color = MetricHeartRate, fontSize = 12.sp)
                    }
                    block.actualCadence?.let {
                        Text("${it.toInt()}rpm", color = MetricCadence, fontSize = 12.sp)
                    }
                }

                // Target vs actual comparison
                if (block.targetPower != null && block.actualPower != null) {
                    val diff = block.actualPower - block.targetPower
                    val diffColor = when {
                        kotlin.math.abs(diff) < 10 -> com.koval.trainingplanner.ui.theme.Success
                        kotlin.math.abs(diff) < 30 -> com.koval.trainingplanner.ui.theme.Warning
                        else -> com.koval.trainingplanner.ui.theme.Danger
                    }
                    Text(
                        "Target: ${block.targetPower.toInt()}W (%+dW)".format(diff.toInt()),
                        color = diffColor,
                        fontSize = 11.sp,
                    )
                }
            }
        }
    }
}

private fun rpeColor(rpe: Int): Color = when {
    rpe <= 3 -> com.koval.trainingplanner.ui.theme.Success
    rpe <= 5 -> com.koval.trainingplanner.ui.theme.IntensityTempo
    rpe <= 7 -> com.koval.trainingplanner.ui.theme.Warning
    rpe <= 9 -> com.koval.trainingplanner.ui.theme.IntensityVo2max
    else -> com.koval.trainingplanner.ui.theme.Danger
}
