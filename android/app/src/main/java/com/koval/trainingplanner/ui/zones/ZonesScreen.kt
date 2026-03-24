package com.koval.trainingplanner.ui.zones

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.koval.trainingplanner.domain.model.SportType
import com.koval.trainingplanner.domain.model.User
import com.koval.trainingplanner.domain.model.Zone
import com.koval.trainingplanner.domain.model.ZoneSystem
import com.koval.trainingplanner.ui.theme.Background
import com.koval.trainingplanner.ui.theme.Border
import com.koval.trainingplanner.ui.theme.IntensityAnaerobic
import com.koval.trainingplanner.ui.theme.Primary
import com.koval.trainingplanner.ui.theme.PrimaryMuted
import com.koval.trainingplanner.ui.theme.SportCycling
import com.koval.trainingplanner.ui.theme.SportRunning
import com.koval.trainingplanner.ui.theme.SportSwimming
import com.koval.trainingplanner.ui.theme.Surface as SurfaceColor
import com.koval.trainingplanner.ui.theme.TextMuted
import com.koval.trainingplanner.ui.theme.TextPrimary
import com.koval.trainingplanner.ui.theme.TextSecondary
import com.koval.trainingplanner.ui.theme.Zone1
import com.koval.trainingplanner.ui.theme.Zone2
import com.koval.trainingplanner.ui.theme.Zone3
import com.koval.trainingplanner.ui.theme.Zone4
import com.koval.trainingplanner.ui.theme.Zone5
import com.koval.trainingplanner.ui.theme.Zone6
import com.koval.trainingplanner.ui.theme.Zone7
import androidx.compose.ui.graphics.Color

@Composable
fun ZonesScreen(viewModel: ZonesViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Background),
    ) {
        // Header
        Text(
            text = "Training Zones",
            color = TextPrimary,
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
        )

        // Sport filter chips
        SportFilterRow(
            selected = state.selectedSport,
            onSelect = { viewModel.selectSport(it) },
        )

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

            else -> {
                val systems = viewModel.filteredSystems()
                if (systems.isEmpty()) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("No zone systems found", color = TextMuted, fontSize = 14.sp)
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        item { Spacer(Modifier.height(4.dp)) }
                        items(systems, key = { it.id }) { system ->
                            ZoneSystemCard(
                                system = system,
                                user = state.user,
                                modifier = Modifier.padding(horizontal = 16.dp),
                            )
                        }
                        item { Spacer(Modifier.height(24.dp)) }
                    }
                }
            }
        }
    }
}

// ── Sport Filter ──

@Composable
private fun SportFilterRow(selected: SportType?, onSelect: (SportType?) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        SportChip("All", selected == null, SportCycling) { onSelect(null) }
        SportChip("Cycling", selected == SportType.CYCLING, SportCycling) { onSelect(SportType.CYCLING) }
        SportChip("Running", selected == SportType.RUNNING, SportRunning) { onSelect(SportType.RUNNING) }
        SportChip("Swimming", selected == SportType.SWIMMING, SportSwimming) { onSelect(SportType.SWIMMING) }
    }
}

@Composable
private fun SportChip(label: String, isSelected: Boolean, accentColor: Color, onClick: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = if (isSelected) accentColor.copy(alpha = 0.15f) else SurfaceColor,
        border = if (isSelected) {
            androidx.compose.foundation.BorderStroke(1.dp, accentColor.copy(alpha = 0.4f))
        } else {
            androidx.compose.foundation.BorderStroke(1.dp, Border)
        },
        modifier = Modifier.clickable { onClick() },
    ) {
        Text(
            text = label,
            color = if (isSelected) accentColor else TextSecondary,
            fontSize = 12.sp,
            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
        )
    }
}

// ── Zone System Card ──

@Composable
private fun ZoneSystemCard(system: ZoneSystem, user: User?, modifier: Modifier = Modifier) {
    val sportColor = when (system.sportType) {
        SportType.CYCLING -> SportCycling
        SportType.RUNNING -> SportRunning
        SportType.SWIMMING -> SportSwimming
        SportType.BRICK -> Primary
    }

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = SurfaceColor,
        border = androidx.compose.foundation.BorderStroke(1.dp, Border),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Header row
            Row(verticalAlignment = Alignment.CenterVertically) {
                // Sport badge
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = sportColor.copy(alpha = 0.12f),
                ) {
                    Text(
                        text = system.sportType.name,
                        color = sportColor,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                    )
                }
                if (system.defaultForSport) {
                    Spacer(Modifier.width(6.dp))
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = PrimaryMuted,
                    ) {
                        Text(
                            text = "DEFAULT",
                            color = Primary,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp),
                        )
                    }
                }
            }

            Spacer(Modifier.height(8.dp))

            // Name
            Text(
                text = system.name,
                color = TextPrimary,
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
            )

            // Reference info
            val refLabel = system.referenceName ?: system.referenceType
            val refUnit = system.referenceUnit?.let { " ($it)" } ?: ""
            Text(
                text = "Reference: $refLabel$refUnit",
                color = TextMuted,
                fontSize = 12.sp,
            )

            Spacer(Modifier.height(12.dp))

            // Column headers
            ZoneTableHeader(system.sportType, user)

            Spacer(Modifier.height(4.dp))

            // Zone rows
            system.zones.forEachIndexed { index, zone ->
                ZoneRow(
                    zone = zone,
                    index = index,
                    sportType = system.sportType,
                    referenceType = system.referenceType,
                    user = user,
                    totalZones = system.zones.size,
                )
                if (index < system.zones.lastIndex) {
                    Spacer(Modifier.height(2.dp))
                }
            }
        }
    }
}

// ── Zone Table Header ──

@Composable
private fun ZoneTableHeader(sportType: SportType, user: User?) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Zone label column
        Text("Zone", color = TextMuted, fontSize = 10.sp, fontWeight = FontWeight.Bold,
            modifier = Modifier.width(40.dp))
        // Range column
        Text("Range", color = TextMuted, fontSize = 10.sp, fontWeight = FontWeight.Bold,
            modifier = Modifier.width(60.dp))
        // Description column
        Text("Description", color = TextMuted, fontSize = 10.sp, fontWeight = FontWeight.Bold,
            modifier = Modifier.weight(1f))

        // Sport-specific columns
        when (sportType) {
            SportType.CYCLING -> {
                if (user?.ftp != null) {
                    Text("Watts", color = TextMuted, fontSize = 10.sp, fontWeight = FontWeight.Bold,
                        modifier = Modifier.width(62.dp))
                }
            }
            SportType.RUNNING -> {
                if (user?.functionalThresholdPace != null) {
                    Text("/km", color = TextMuted, fontSize = 10.sp, fontWeight = FontWeight.Bold,
                        modifier = Modifier.width(52.dp))
                    Text("/400m", color = TextMuted, fontSize = 10.sp, fontWeight = FontWeight.Bold,
                        modifier = Modifier.width(50.dp))
                    Text("/100m", color = TextMuted, fontSize = 10.sp, fontWeight = FontWeight.Bold,
                        modifier = Modifier.width(40.dp))
                }
            }
            SportType.SWIMMING -> {
                if (user?.criticalSwimSpeed != null) {
                    Text("/100m", color = TextMuted, fontSize = 10.sp, fontWeight = FontWeight.Bold,
                        modifier = Modifier.width(56.dp))
                }
            }
            else -> {}
        }
    }
}

// ── Zone Row ──

@Composable
private fun ZoneRow(
    zone: Zone,
    index: Int,
    sportType: SportType,
    referenceType: String,
    user: User?,
    totalZones: Int,
) {
    val zoneColor = getZoneColor(index, totalZones)

    Surface(
        shape = RoundedCornerShape(8.dp),
        color = zoneColor.copy(alpha = 0.08f),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Zone label with color indicator
            Row(
                modifier = Modifier.width(40.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .background(zoneColor, RoundedCornerShape(2.dp)),
                )
                Spacer(Modifier.width(4.dp))
                Text(
                    text = zone.label,
                    color = zoneColor,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                )
            }

            // Range
            Text(
                text = "${zone.low}-${zone.high}%",
                color = TextSecondary,
                fontSize = 11.sp,
                modifier = Modifier.width(60.dp),
            )

            // Description
            Text(
                text = zone.description ?: "",
                color = TextPrimary,
                fontSize = 11.sp,
                modifier = Modifier.weight(1f),
                maxLines = 1,
            )

            // Sport-specific absolute values — skip when low or high is 0
            when (sportType) {
                SportType.CYCLING -> {
                    val ftp = user?.ftp
                    if (ftp != null) {
                        val lowW = if (zone.low > 0) zone.low * ftp / 100 else null
                        val highW = if (zone.high > 0) zone.high * ftp / 100 else null
                        val text = when {
                            lowW != null && highW != null -> "$lowW-${highW}W"
                            highW != null -> "≤${highW}W"
                            lowW != null -> "≥${lowW}W"
                            else -> ""
                        }
                        if (text.isNotEmpty()) {
                            Text(
                                text = text,
                                color = zoneColor,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium,
                                modifier = Modifier.width(62.dp),
                            )
                        } else {
                            Spacer(Modifier.width(62.dp))
                        }
                    }
                }
                SportType.RUNNING -> {
                    val tp = user?.functionalThresholdPace
                    if (tp != null) {
                        if (zone.low > 0 && zone.high > 0) {
                            val slowPace = tp.toFloat() / (zone.low.toFloat() / 100f)
                            val fastPace = tp.toFloat() / (zone.high.toFloat() / 100f)

                            Text(
                                text = "${formatPace(fastPace)}-${formatPace(slowPace)}",
                                color = zoneColor,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Medium,
                                modifier = Modifier.width(52.dp),
                            )
                            Text(
                                text = "${formatPace(fastPace * 0.4f)}-${formatPace(slowPace * 0.4f)}",
                                color = zoneColor,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Medium,
                                modifier = Modifier.width(50.dp),
                            )
                            val fast100 = (fastPace * 0.1f).toInt()
                            val slow100 = (slowPace * 0.1f).toInt()
                            Text(
                                text = "${fast100}-${slow100}s",
                                color = zoneColor,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Medium,
                                modifier = Modifier.width(40.dp),
                            )
                        } else {
                            Spacer(Modifier.width(142.dp))
                        }
                    }
                }
                SportType.SWIMMING -> {
                    val css = user?.criticalSwimSpeed
                    if (css != null) {
                        if (zone.low > 0 && zone.high > 0) {
                            val slowPace = css.toFloat() / (zone.low.toFloat() / 100f)
                            val fastPace = css.toFloat() / (zone.high.toFloat() / 100f)
                            Text(
                                text = "${formatPace(fastPace)}-${formatPace(slowPace)}",
                                color = zoneColor,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium,
                                modifier = Modifier.width(56.dp),
                            )
                        } else {
                            Spacer(Modifier.width(56.dp))
                        }
                    }
                }
                else -> {}
            }
        }
    }
}

// ── Helpers ──

private fun getZoneColor(index: Int, total: Int): Color {
    // Map zone index to standard zone colors
    val colors = listOf(Zone1, Zone2, Zone3, Zone4, Zone5, Zone6, Zone7)
    return if (index < colors.size) colors[index] else IntensityAnaerobic
}

/** Formats seconds to m:ss */
private fun formatPace(totalSeconds: Float): String {
    val secs = totalSeconds.toInt()
    val m = secs / 60
    val s = secs % 60
    return "$m:%02d".format(s)
}
