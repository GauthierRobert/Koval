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
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.IconButton
import androidx.compose.material3.FilterChipDefaults
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

            // Source filter
            SourceFilterRow(
                selected = state.sourceFilter,
                onSelect = viewModel::setSourceFilter,
                clubCount = state.clubTrainings.size,
            )

            // Sport filter
            SportFilterRow(
                selected = state.sportFilter,
                onSelect = viewModel::setSportFilter,
            )

            // Type filter
            TypeFilterRow(
                selected = state.typeFilter,
                onSelect = viewModel::setTypeFilter,
            )

            Spacer(Modifier.height(8.dp))

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
                        verticalArrangement = Arrangement.spacedBy(8.dp),
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

@Composable
private fun SourceFilterRow(
    selected: TrainingSource,
    onSelect: (TrainingSource) -> Unit,
    clubCount: Int,
) {
    Row(
        modifier = Modifier.padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        SourceChip("My Trainings", selected == TrainingSource.MY) { onSelect(TrainingSource.MY) }
        SourceChip("Club ($clubCount)", selected == TrainingSource.CLUB) { onSelect(TrainingSource.CLUB) }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SourceChip(label: String, selected: Boolean, onClick: () -> Unit) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(label, fontSize = 13.sp) },
        colors = FilterChipDefaults.filterChipColors(
            selectedContainerColor = Primary.copy(alpha = 0.15f),
            selectedLabelColor = Primary,
            containerColor = SurfaceColor,
            labelColor = TextSecondary,
        ),
        border = FilterChipDefaults.filterChipBorder(
            borderColor = Border,
            selectedBorderColor = Primary.copy(alpha = 0.3f),
            enabled = true,
            selected = selected,
        ),
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SportFilterRow(selected: SportType?, onSelect: (SportType?) -> Unit) {
    FlowRow(
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        SportType.entries.forEach { sport ->
            SportFilterChip(sport, selected == sport) { onSelect(sport) }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SportFilterChip(sport: SportType, selected: Boolean, onClick: () -> Unit) {
    val sportColor = sportColor(sport)
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(sport.name.lowercase().replaceFirstChar { it.uppercase() }, fontSize = 12.sp) },
        colors = FilterChipDefaults.filterChipColors(
            selectedContainerColor = sportColor.copy(alpha = 0.15f),
            selectedLabelColor = sportColor,
            containerColor = Color.Transparent,
            labelColor = TextMuted,
        ),
        border = FilterChipDefaults.filterChipBorder(
            borderColor = Border,
            selectedBorderColor = sportColor.copy(alpha = 0.3f),
            enabled = true,
            selected = selected,
        ),
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TypeFilterRow(selected: TrainingType?, onSelect: (TrainingType?) -> Unit) {
    FlowRow(
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        TrainingType.entries.forEach { type ->
            TypeFilterChip(type, selected == type) { onSelect(type) }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TypeFilterChip(type: TrainingType, selected: Boolean, onClick: () -> Unit) {
    val color = trainingTypeColor(type)
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(type.name.replace("_", " ").lowercase().replaceFirstChar { it.uppercase() }, fontSize = 11.sp) },
        colors = FilterChipDefaults.filterChipColors(
            selectedContainerColor = color.copy(alpha = 0.15f),
            selectedLabelColor = color,
            containerColor = Color.Transparent,
            labelColor = TextMuted,
        ),
        border = FilterChipDefaults.filterChipBorder(
            borderColor = Border,
            selectedBorderColor = color.copy(alpha = 0.3f),
            enabled = true,
            selected = selected,
        ),
    )
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
        shape = RoundedCornerShape(14.dp),
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
                    .width(4.dp)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(topStart = 14.dp, bottomStart = 14.dp))
                    .background(sportColor(training.sportType)),
            )

            Row(
                modifier = Modifier.padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                SportIcon(sport = training.sportType)
                Spacer(Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = training.title,
                        color = TextPrimary,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                    )

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        training.estimatedDurationSeconds?.let {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Filled.Timer, null, tint = TextMuted, modifier = Modifier.height(13.dp))
                                Spacer(Modifier.width(2.dp))
                                Text(formatDuration(it), color = TextSecondary, fontSize = 13.sp)
                            }
                        }
                        training.estimatedTss?.let {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Filled.Speed, null, tint = TextMuted, modifier = Modifier.height(13.dp))
                                Spacer(Modifier.width(2.dp))
                                Text("$it TSS", color = TextSecondary, fontSize = 13.sp)
                            }
                        }
                        training.estimatedIf?.let {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Filled.FitnessCenter, null, tint = TextMuted, modifier = Modifier.height(13.dp))
                                Spacer(Modifier.width(2.dp))
                                Text("IF %.2f".format(it), color = TextSecondary, fontSize = 13.sp)
                            }
                        }
                    }
                }

                // Training type pill
                training.trainingType?.let { type ->
                    TrainingTypePillSmall(type)
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
