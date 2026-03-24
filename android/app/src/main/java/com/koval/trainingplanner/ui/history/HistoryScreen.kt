package com.koval.trainingplanner.ui.history

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
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
import com.koval.trainingplanner.domain.model.CompletedSession
import com.koval.trainingplanner.domain.model.PmcDataPoint
import com.koval.trainingplanner.domain.model.SportType
import com.koval.trainingplanner.ui.calendar.components.SportIcon
import com.koval.trainingplanner.ui.calendar.components.formatDuration
import com.koval.trainingplanner.ui.theme.Background
import com.koval.trainingplanner.ui.theme.Border
import com.koval.trainingplanner.ui.theme.MetricFatigue
import com.koval.trainingplanner.ui.theme.MetricFitness
import com.koval.trainingplanner.ui.theme.MetricFormNegative
import com.koval.trainingplanner.ui.theme.MetricFormPositive
import com.koval.trainingplanner.ui.theme.MetricHeartRate
import com.koval.trainingplanner.ui.theme.MetricPower
import com.koval.trainingplanner.ui.theme.Primary
import com.koval.trainingplanner.ui.theme.SportBrick
import com.koval.trainingplanner.ui.theme.SportCycling
import com.koval.trainingplanner.ui.theme.SportRunning
import com.koval.trainingplanner.ui.theme.SportSwimming
import com.koval.trainingplanner.ui.theme.Surface as SurfaceColor
import com.koval.trainingplanner.ui.theme.TextMuted
import com.koval.trainingplanner.ui.theme.TextPrimary
import com.koval.trainingplanner.ui.theme.TextSecondary
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(
    onSessionClick: (String) -> Unit,
    viewModel: HistoryViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsState()
    val sessions = viewModel.filteredSessions()

    PullToRefreshBox(
        isRefreshing = state.isRefreshing,
        onRefresh = { viewModel.loadSessions(isRefresh = true) },
        modifier = Modifier
            .fillMaxSize()
            .background(Background),
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Header
            Text(
                text = "History",
                color = TextPrimary,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            )

            // PMC summary card
            state.pmcLatest?.let { pmc ->
                PmcSummaryCard(
                    pmc = pmc,
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
                Spacer(Modifier.height(8.dp))
            }

            // Sport filter
            SportFilterRow(
                selected = state.sportFilter,
                onSelect = viewModel::setSportFilter,
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

                sessions.isEmpty() -> {
                    Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
                        Text("No sessions recorded yet", color = TextMuted, fontSize = 15.sp)
                    }
                }

                else -> {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        items(sessions, key = { it.id }) { session ->
                            SessionCard(
                                session = session,
                                onClick = { onSessionClick(session.id) },
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
private fun PmcSummaryCard(pmc: PmcDataPoint, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        color = SurfaceColor,
        border = androidx.compose.foundation.BorderStroke(1.dp, Border),
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            PmcMetric("Fitness", "%.0f".format(pmc.ctl), MetricFitness)
            PmcMetric("Fatigue", "%.0f".format(pmc.atl), MetricFatigue)
            PmcMetric("Form", "%+.0f".format(pmc.tsb), if (pmc.tsb >= 0) MetricFormPositive else MetricFormNegative)
        }
    }
}

@Composable
private fun PmcMetric(label: String, value: String, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, color = color, fontSize = 22.sp, fontWeight = FontWeight.Bold)
        Text(label, color = TextMuted, fontSize = 11.sp)
    }
}

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
private fun SportFilterRow(selected: SportType?, onSelect: (SportType?) -> Unit) {
    FlowRow(
        modifier = Modifier.padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        SportType.entries.forEach { sport ->
            val color = sportColor(sport)
            FilterChip(
                selected = selected == sport,
                onClick = { onSelect(sport) },
                label = { Text(sport.name.lowercase().replaceFirstChar { it.uppercase() }, fontSize = 12.sp) },
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
                    selected = selected == sport,
                ),
            )
        }
    }
}

@Composable
private fun SessionCard(
    session: CompletedSession,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val sportColor = sportColor(session.sportType)

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
                    .background(sportColor),
            )

            Column(modifier = Modifier.padding(12.dp)) {
                // Date
                session.completedAt?.let { dateStr ->
                    val formatted = try {
                        ZonedDateTime.parse(dateStr).format(DateTimeFormatter.ofPattern("EEE, MMM d 'at' HH:mm"))
                    } catch (_: Exception) {
                        dateStr.take(16)
                    }
                    Text(formatted, color = TextMuted, fontSize = 11.sp)
                }

                Spacer(Modifier.height(4.dp))

                Row(verticalAlignment = Alignment.CenterVertically) {
                    SportIcon(sport = session.sportType)
                    Spacer(Modifier.width(12.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = session.title ?: "Session",
                            color = TextPrimary,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Medium,
                            maxLines = 1,
                        )

                        Row(
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier.padding(top = 2.dp),
                        ) {
                            session.totalDurationSeconds?.let {
                                MetricInline(Icons.Filled.Timer, formatDuration(it), TextSecondary)
                            }
                            session.avgPower?.let {
                                MetricInline(Icons.Filled.Bolt, "${it.toInt()}W", MetricPower)
                            }
                            session.avgHR?.let {
                                MetricInline(Icons.Filled.Favorite, "${it.toInt()}bpm", MetricHeartRate)
                            }
                        }
                    }

                    // TSS badge
                    session.tss?.let { tss ->
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = Primary.copy(alpha = 0.12f),
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                            ) {
                                Text("${tss.toInt()}", color = Primary, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                                Text("TSS", color = Primary.copy(alpha = 0.7f), fontSize = 9.sp)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MetricInline(icon: androidx.compose.ui.graphics.vector.ImageVector, value: String, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = color.copy(alpha = 0.7f), modifier = Modifier.size(13.dp))
        Spacer(Modifier.width(2.dp))
        Text(value, color = color, fontSize = 12.sp)
    }
}

private fun sportColor(sport: SportType): Color = when (sport) {
    SportType.CYCLING -> SportCycling
    SportType.RUNNING -> SportRunning
    SportType.SWIMMING -> SportSwimming
    SportType.BRICK -> SportBrick
}
