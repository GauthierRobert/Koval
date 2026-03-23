package com.koval.trainingplanner.ui.training

import androidx.compose.foundation.Canvas
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
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.koval.trainingplanner.domain.model.BlockType
import com.koval.trainingplanner.domain.model.Training
import com.koval.trainingplanner.domain.model.TrainingType
import com.koval.trainingplanner.domain.model.WorkoutElement
import com.koval.trainingplanner.ui.calendar.components.SportIcon
import com.koval.trainingplanner.ui.calendar.components.formatDuration
import com.koval.trainingplanner.ui.theme.Background
import com.koval.trainingplanner.ui.theme.Border
import com.koval.trainingplanner.ui.theme.Primary
import com.koval.trainingplanner.ui.theme.Surface as SurfaceColor
import com.koval.trainingplanner.ui.theme.SurfaceElevated
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

@Composable
fun TrainingDetailScreen(
    onBack: () -> Unit,
    viewModel: TrainingDetailViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Background),
    ) {
        // Top bar with back button
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = TextPrimary,
                )
            }
            Text(
                text = "Training Details",
                color = TextPrimary,
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold,
            )
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

            state.training != null -> {
                TrainingDetailContent(training = state.training!!)
            }
        }
    }
}

@Composable
private fun TrainingDetailContent(training: Training) {
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
                border = androidx.compose.foundation.BorderStroke(1.dp, Border),
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        SportIcon(sport = training.sportType, size = 48.dp, iconSize = 24.dp)
                        Spacer(Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = training.title,
                                color = TextPrimary,
                                fontSize = 20.sp,
                                fontWeight = FontWeight.Bold,
                            )
                            training.trainingType?.let {
                                TrainingTypePill(it)
                            }
                        }
                    }

                    training.description?.takeIf { it.isNotBlank() }?.let {
                        Spacer(Modifier.height(12.dp))
                        Text(
                            text = it,
                            color = TextSecondary,
                            fontSize = 14.sp,
                            lineHeight = 20.sp,
                        )
                    }

                    // Metrics row
                    Spacer(Modifier.height(16.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                    ) {
                        training.estimatedDurationSeconds?.let {
                            MetricChip(Icons.Filled.Timer, formatDuration(it), "Duration")
                        }
                        training.estimatedTss?.let {
                            MetricChip(Icons.Filled.Speed, "$it", "TSS")
                        }
                        training.estimatedIf?.let {
                            MetricChip(Icons.Filled.FitnessCenter, "%.2f".format(it), "IF")
                        }
                    }
                }
            }
        }

        // Intensity profile graph
        val flatBlocks = flattenBlocks(training.blocks)
        if (flatBlocks.isNotEmpty()) {
            item {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    shape = RoundedCornerShape(16.dp),
                    color = SurfaceColor,
                    border = androidx.compose.foundation.BorderStroke(1.dp, Border),
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            "Intensity Profile",
                            color = TextPrimary,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Spacer(Modifier.height(12.dp))
                        IntensityGraph(
                            blocks = flatBlocks,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(160.dp),
                        )
                        // Zone legend
                        Spacer(Modifier.height(8.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceEvenly,
                        ) {
                            ZoneLegend("Z1", TypeRecovery)
                            ZoneLegend("Z2", TypeEndurance)
                            ZoneLegend("SS", TypeSweetSpot)
                            ZoneLegend("Z4", TypeThreshold)
                            ZoneLegend("Z5", TypeVo2max)
                        }
                    }
                }
            }
        }

        // Workout steps
        item {
            Text(
                "Workout Steps",
                color = TextSecondary,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 2.sp,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
            )
        }

        itemsIndexed(training.blocks) { index, element ->
            WorkoutElementCard(
                element = element,
                index = index + 1,
                modifier = Modifier.padding(horizontal = 16.dp),
            )
        }

        item { Spacer(Modifier.height(24.dp)) }
    }
}

// ── Intensity Profile Graph ──

private data class FlatBlock(
    val durationSeconds: Int,
    val intensityStart: Int,
    val intensityEnd: Int,
    val type: BlockType,
)

private fun flattenBlocks(elements: List<WorkoutElement>): List<FlatBlock> {
    val result = mutableListOf<FlatBlock>()
    for (element in elements) {
        if (element.isSet) {
            val reps = element.repetitions ?: 1
            repeat(reps) { rep ->
                element.elements?.forEach { child ->
                    result.addAll(flattenBlocks(listOf(child)))
                }
                // Add rest between reps (except after last)
                if (rep < reps - 1 && element.restDurationSeconds != null && element.restDurationSeconds > 0) {
                    result.add(
                        FlatBlock(
                            durationSeconds = element.restDurationSeconds,
                            intensityStart = element.restIntensity ?: 40,
                            intensityEnd = element.restIntensity ?: 40,
                            type = BlockType.FREE,
                        )
                    )
                }
            }
        } else {
            val duration = element.durationSeconds ?: 60
            val type = element.type ?: BlockType.STEADY
            val start: Int
            val end: Int
            if (type == BlockType.RAMP) {
                start = element.intensityStart ?: element.intensityTarget ?: 50
                end = element.intensityEnd ?: element.intensityTarget ?: 50
            } else {
                val intensity = element.intensityTarget ?: 50
                start = intensity
                end = intensity
            }
            result.add(FlatBlock(duration, start, end, type))
        }
    }
    return result
}

private fun blockColor(type: BlockType): Color = when (type) {
    BlockType.WARMUP -> TypeEndurance
    BlockType.COOLDOWN -> TypeRecovery
    BlockType.STEADY -> TypeSweetSpot
    BlockType.INTERVAL -> TypeVo2max
    BlockType.RAMP -> TypeThreshold
    BlockType.FREE -> TypeMixed
    BlockType.PAUSE -> TextMuted
}

private fun intensityColor(intensity: Int): Color = when {
    intensity < 56 -> TypeRecovery
    intensity < 76 -> TypeEndurance
    intensity < 91 -> TypeSweetSpot
    intensity < 106 -> TypeThreshold
    else -> TypeVo2max
}

@Composable
private fun IntensityGraph(blocks: List<FlatBlock>, modifier: Modifier = Modifier) {
    val totalDuration = blocks.sumOf { it.durationSeconds }.coerceAtLeast(1)
    val maxIntensity = blocks.maxOf { maxOf(it.intensityStart, it.intensityEnd) }.coerceAtLeast(100)
    val yScale = maxIntensity + 10 // 10% headroom

    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height

        // Draw zone guidelines
        val zones = listOf(55, 75, 90, 105)
        for (zone in zones) {
            val y = h - (zone.toFloat() / yScale) * h
            drawLine(
                color = Border,
                start = Offset(0f, y),
                end = Offset(w, y),
                strokeWidth = 1f,
            )
        }

        // Draw blocks
        var xOffset = 0f
        for (block in blocks) {
            val blockWidth = (block.durationSeconds.toFloat() / totalDuration) * w
            val yStart = h - (block.intensityStart.toFloat() / yScale) * h
            val yEnd = h - (block.intensityEnd.toFloat() / yScale) * h
            val color = blockColor(block.type)

            if (block.type == BlockType.RAMP) {
                // Draw as a trapezoid with gradient fill
                val path = Path().apply {
                    moveTo(xOffset, h)
                    lineTo(xOffset, yStart)
                    lineTo(xOffset + blockWidth, yEnd)
                    lineTo(xOffset + blockWidth, h)
                    close()
                }
                drawPath(
                    path = path,
                    brush = Brush.verticalGradient(
                        colors = listOf(color.copy(alpha = 0.8f), color.copy(alpha = 0.2f)),
                        startY = minOf(yStart, yEnd),
                        endY = h,
                    ),
                )
                // Outline top edge
                drawLine(
                    color = color,
                    start = Offset(xOffset, yStart),
                    end = Offset(xOffset + blockWidth, yEnd),
                    strokeWidth = 2f,
                )
            } else {
                val blockHeight = h - yStart
                // Filled rect
                drawRect(
                    brush = Brush.verticalGradient(
                        colors = listOf(color.copy(alpha = 0.7f), color.copy(alpha = 0.15f)),
                        startY = yStart,
                        endY = h,
                    ),
                    topLeft = Offset(xOffset, yStart),
                    size = Size(blockWidth, blockHeight),
                )
                // Top edge
                drawLine(
                    color = color,
                    start = Offset(xOffset, yStart),
                    end = Offset(xOffset + blockWidth, yStart),
                    strokeWidth = 2f,
                )
            }

            // Separator line
            if (xOffset > 0f) {
                drawLine(
                    color = Background.copy(alpha = 0.5f),
                    start = Offset(xOffset, 0f),
                    end = Offset(xOffset, h),
                    strokeWidth = 1f,
                )
            }
            xOffset += blockWidth
        }
    }
}

@Composable
private fun ZoneLegend(label: String, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .background(color, RoundedCornerShape(2.dp)),
        )
        Spacer(Modifier.width(4.dp))
        Text(label, color = TextMuted, fontSize = 10.sp)
    }
}

// ── Workout Element Cards ──

@Composable
private fun WorkoutElementCard(
    element: WorkoutElement,
    index: Int,
    modifier: Modifier = Modifier,
    nestLevel: Int = 0,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        color = if (nestLevel == 0) SurfaceColor else SurfaceElevated,
        border = androidx.compose.foundation.BorderStroke(1.dp, Border),
    ) {
        if (element.isSet) {
            SetElementContent(element, index, nestLevel)
        } else {
            LeafElementContent(element, index)
        }
    }
}

@Composable
private fun SetElementContent(element: WorkoutElement, index: Int, nestLevel: Int) {
    Column(modifier = Modifier.padding(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            // Repeat badge
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = Primary.copy(alpha = 0.12f),
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        Icons.Filled.Repeat,
                        contentDescription = null,
                        tint = Primary,
                        modifier = Modifier.size(14.dp),
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        "${element.repetitions ?: 1}x",
                        color = Primary,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }

            Spacer(Modifier.width(8.dp))
            Text(
                "Set $index",
                color = TextPrimary,
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
            )

            element.restDurationSeconds?.let { rest ->
                Spacer(Modifier.width(8.dp))
                Text(
                    "rest ${formatDuration(rest)}",
                    color = TextSecondary,
                    fontSize = 12.sp,
                    fontStyle = FontStyle.Italic,
                )
            }
        }

        Spacer(Modifier.height(8.dp))

        element.elements?.forEachIndexed { childIdx, child ->
            if (childIdx > 0) {
                HorizontalDivider(
                    color = Border,
                    modifier = Modifier.padding(vertical = 6.dp),
                )
            }
            if (child.isSet) {
                WorkoutElementCard(
                    element = child,
                    index = childIdx + 1,
                    nestLevel = nestLevel + 1,
                )
            } else {
                LeafElementContent(child, childIdx + 1)
            }
        }
    }
}

@Composable
private fun LeafElementContent(element: WorkoutElement, index: Int) {
    val type = element.type ?: BlockType.STEADY
    val color = blockColor(type)

    Row(
        modifier = Modifier.padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Type color indicator
        Box(
            modifier = Modifier
                .size(36.dp)
                .background(color.copy(alpha = 0.15f), RoundedCornerShape(10.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = type.name.take(3),
                color = color,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
            )
        }

        Spacer(Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            // Label
            Text(
                text = element.label ?: type.name.lowercase()
                    .replaceFirstChar { it.uppercase() },
                color = TextPrimary,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
            )

            // Metadata row
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                element.durationSeconds?.let {
                    Text(formatDuration(it), color = TextSecondary, fontSize = 12.sp)
                }
                element.distanceMeters?.let {
                    Text("${it}m", color = TextSecondary, fontSize = 12.sp)
                }
                // Intensity
                when {
                    type == BlockType.RAMP && element.intensityStart != null && element.intensityEnd != null -> {
                        Text(
                            "${element.intensityStart}% → ${element.intensityEnd}%",
                            color = color,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }

                    element.intensityTarget != null -> {
                        Text(
                            "${element.intensityTarget}%",
                            color = intensityColor(element.intensityTarget),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }
                element.cadenceTarget?.let {
                    Text("${it}rpm", color = TextSecondary, fontSize = 12.sp)
                }
            }

            // Zone label
            element.zoneLabel?.let {
                Text(it, color = TextMuted, fontSize = 11.sp)
            }

            // Description
            element.description?.takeIf { it.isNotBlank() }?.let {
                Text(
                    it,
                    color = TextSecondary,
                    fontSize = 12.sp,
                    fontStyle = FontStyle.Italic,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
        }
    }
}

// ── Shared Components ──

@Composable
private fun MetricChip(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    value: String,
    label: String,
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(icon, contentDescription = null, tint = Primary, modifier = Modifier.size(18.dp))
        Spacer(Modifier.height(4.dp))
        Text(value, color = TextPrimary, fontSize = 16.sp, fontWeight = FontWeight.Bold)
        Text(label, color = TextSecondary, fontSize = 11.sp)
    }
}

@Composable
private fun TrainingTypePill(type: TrainingType) {
    val color = when (type) {
        TrainingType.VO2MAX -> TypeVo2max
        TrainingType.THRESHOLD -> TypeThreshold
        TrainingType.SWEET_SPOT -> TypeSweetSpot
        TrainingType.ENDURANCE -> TypeEndurance
        TrainingType.SPRINT -> TypeSprint
        TrainingType.RECOVERY -> TypeRecovery
        TrainingType.MIXED -> TypeMixed
        TrainingType.TEST -> TypeTest
    }
    Surface(
        shape = RoundedCornerShape(6.dp),
        color = color.copy(alpha = 0.12f),
        modifier = Modifier.padding(top = 4.dp),
    ) {
        Text(
            text = type.name.replace("_", " "),
            color = color,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
        )
    }
}
