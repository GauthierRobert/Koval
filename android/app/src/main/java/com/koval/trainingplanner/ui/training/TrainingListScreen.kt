package com.koval.trainingplanner.ui.training

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.material.icons.filled.Add
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.koval.trainingplanner.domain.model.SportType
import com.koval.trainingplanner.domain.model.Training
import com.koval.trainingplanner.domain.model.TrainingType
import com.koval.trainingplanner.ui.calendar.components.SportIcon
import com.koval.trainingplanner.ui.calendar.components.formatDuration
import com.koval.trainingplanner.ui.theme.Background
import com.koval.trainingplanner.ui.theme.Border
import com.koval.trainingplanner.ui.theme.Primary
import com.koval.trainingplanner.ui.theme.Surface as SurfaceColor
import com.koval.trainingplanner.ui.theme.TextMuted
import com.koval.trainingplanner.ui.theme.TextPrimary
import com.koval.trainingplanner.ui.theme.TextSecondary
import com.koval.trainingplanner.ui.theme.TypeEndurance
import com.koval.trainingplanner.ui.theme.TypeMixed
import com.koval.trainingplanner.ui.theme.TypeRecovery
import com.koval.trainingplanner.ui.theme.TypeSprint
import com.koval.trainingplanner.ui.theme.TypeSweetSpot
import com.koval.trainingplanner.ui.theme.TypeTest
import com.koval.trainingplanner.ui.theme.TypeThreshold
import com.koval.trainingplanner.ui.theme.TypeVo2max

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TrainingListScreen(
    onTrainingClick: (String) -> Unit,
    onCreateClick: () -> Unit = {},
    viewModel: TrainingListViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsState()
    val trainings = viewModel.filteredTrainings()

    PullToRefreshBox(
        isRefreshing = state.isRefreshing,
        onRefresh = { viewModel.loadTrainings(isRefresh = true) },
        modifier = Modifier
            .fillMaxSize()
            .background(Background),
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "Trainings",
                    color = TextPrimary,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = onCreateClick) {
                    Icon(Icons.Filled.Add, "Create workout", tint = Primary)
                }
            }

            // Filters (source, sport, type) — compact
            SourceFilterRow(
                contexts = state.sourceContexts,
                activeKey = state.activeSource,
                onSelect = viewModel::setActiveSource,
            )
            SportFilterRow(
                selected = state.sportFilter,
                onSelect = viewModel::setSportFilter,
            )
            TypeFilterRow(
                selected = state.typeFilter,
                onSelect = viewModel::setTypeFilter,
            )
            Spacer(Modifier.height(4.dp))

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

                trainings.isEmpty() -> {
                    Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
                        Text("No trainings found", color = TextMuted, fontSize = 15.sp)
                    }
                }

                else -> {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        items(trainings, key = { it.id }) { training ->
                            TrainingCard(
                                training = training,
                                onClick = { onTrainingClick(training.id) },
                                modifier = Modifier.padding(horizontal = 16.dp),
                            )
                        }
                        item { Spacer(Modifier.height(16.dp)) }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SourceFilterRow(
    contexts: List<TrainingSourceContext>,
    activeKey: String,
    onSelect: (String) -> Unit,
) {
    FlowRow(
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 2.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        contexts.forEach { ctx ->
            CompactChip(ctx.label, activeKey == ctx.key, Primary) { onSelect(ctx.key) }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SportFilterRow(selected: SportType?, onSelect: (SportType?) -> Unit) {
    FlowRow(
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 2.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        SportType.entries.forEach { sport ->
            CompactChip(
                label = sport.name.lowercase().replaceFirstChar { it.uppercase() },
                selected = selected == sport,
                accentColor = sportColor(sport),
            ) { onSelect(sport) }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TypeFilterRow(selected: TrainingType?, onSelect: (TrainingType?) -> Unit) {
    FlowRow(
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 2.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        TrainingType.entries.forEach { type ->
            CompactChip(
                label = type.name.replace("_", " ").lowercase().replaceFirstChar { it.uppercase() },
                selected = selected == type,
                accentColor = trainingTypeColor(type),
            ) { onSelect(type) }
        }
    }
}

@Composable
private fun CompactChip(
    label: String,
    selected: Boolean,
    accentColor: Color,
    onClick: () -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = if (selected) accentColor.copy(alpha = 0.15f) else Color.Transparent,
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            if (selected) accentColor.copy(alpha = 0.3f) else Border,
        ),
        modifier = Modifier.clickable(onClick = onClick),
    ) {
        Text(
            text = label,
            color = if (selected) accentColor else TextMuted,
            fontSize = 11.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
        )
    }
}

@Composable
private fun TrainingCard(
    training: Training,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
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
            // Left color bar based on sport
            Box(
                modifier = Modifier
                    .width(3.dp)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(topStart = 10.dp, bottomStart = 10.dp))
                    .background(sportColor(training.sportType)),
            )

            Row(
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                SportIcon(sport = training.sportType)
                Spacer(Modifier.width(10.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = training.title,
                            color = TextPrimary,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium,
                            maxLines = 1,
                            modifier = Modifier.weight(1f, fill = false),
                        )
                        training.trainingType?.let { type ->
                            Spacer(Modifier.width(6.dp))
                            TrainingTypePillSmall(type)
                        }
                    }

                    // Metrics in a single compact row
                    val metricParts = buildList {
                        training.estimatedDurationSeconds?.let { add(formatDuration(it)) }
                        training.estimatedTss?.let { add("$it TSS") }
                        training.estimatedIf?.let { add("IF %.2f".format(it)) }
                    }
                    if (metricParts.isNotEmpty()) {
                        Text(
                            text = metricParts.joinToString(" · "),
                            color = TextSecondary,
                            fontSize = 12.sp,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TrainingTypePillSmall(type: TrainingType) {
    val color = trainingTypeColor(type)
    Surface(
        shape = RoundedCornerShape(6.dp),
        color = color.copy(alpha = 0.12f),
    ) {
        Text(
            text = type.name.replace("_", " "),
            color = color,
            fontSize = 10.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
        )
    }
}

private fun sportColor(sport: SportType): Color = when (sport) {
    SportType.CYCLING -> com.koval.trainingplanner.ui.theme.SportCycling
    SportType.RUNNING -> com.koval.trainingplanner.ui.theme.SportRunning
    SportType.SWIMMING -> com.koval.trainingplanner.ui.theme.SportSwimming
    SportType.BRICK -> com.koval.trainingplanner.ui.theme.SportBrick
}

private fun trainingTypeColor(type: TrainingType): Color = when (type) {
    TrainingType.VO2MAX -> TypeVo2max
    TrainingType.THRESHOLD -> TypeThreshold
    TrainingType.SWEET_SPOT -> TypeSweetSpot
    TrainingType.ENDURANCE -> TypeEndurance
    TrainingType.SPRINT -> TypeSprint
    TrainingType.RECOVERY -> TypeRecovery
    TrainingType.MIXED -> TypeMixed
    TrainingType.TEST -> TypeTest
}
