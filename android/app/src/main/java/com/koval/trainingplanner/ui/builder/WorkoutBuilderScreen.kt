package com.koval.trainingplanner.ui.builder

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.koval.trainingplanner.domain.model.BlockType
import com.koval.trainingplanner.domain.model.SportType
import com.koval.trainingplanner.domain.model.TrainingType
import com.koval.trainingplanner.domain.model.WorkoutElement
import com.koval.trainingplanner.ui.calendar.components.formatDuration
import com.koval.trainingplanner.ui.theme.Background
import com.koval.trainingplanner.ui.theme.BlockCooldown
import com.koval.trainingplanner.ui.theme.BlockFree
import com.koval.trainingplanner.ui.theme.BlockInterval
import com.koval.trainingplanner.ui.theme.BlockPause
import com.koval.trainingplanner.ui.theme.BlockRamp
import com.koval.trainingplanner.ui.theme.BlockSteady
import com.koval.trainingplanner.ui.theme.BlockWarmup
import com.koval.trainingplanner.ui.theme.Border
import com.koval.trainingplanner.ui.theme.Danger
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
fun WorkoutBuilderScreen(
    onBack: () -> Unit,
    viewModel: WorkoutBuilderViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsState()
    var showBlockSheet by remember { mutableStateOf(false) }
    var editBlockIndex by remember { mutableIntStateOf(-1) }

    LaunchedEffect(state.saveSuccess) {
        if (state.saveSuccess) onBack()
    }

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
            Text(
                if (state.isEditMode) "Edit Workout" else "Create Workout",
                color = TextPrimary,
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f),
            )
            // Save button
            IconButton(
                onClick = { viewModel.save() },
                enabled = !state.isSaving,
            ) {
                if (state.isSaving) {
                    CircularProgressIndicator(color = Primary, modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                } else {
                    Icon(Icons.Filled.Save, "Save", tint = Primary)
                }
            }
        }

        if (state.isLoading) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = Primary)
            }
            return@Column
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            // Error
            state.error?.let { error ->
                item {
                    Text(
                        error,
                        color = Danger,
                        fontSize = 13.sp,
                        modifier = Modifier.padding(horizontal = 16.dp),
                    )
                }
            }

            // Title
            item {
                BuilderTextField(
                    value = state.title,
                    onValueChange = viewModel::setTitle,
                    label = "Title",
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
            }

            // Description
            item {
                BuilderTextField(
                    value = state.description,
                    onValueChange = viewModel::setDescription,
                    label = "Description (optional)",
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
            }

            // Sport type
            item {
                SportTypeSelector(
                    selected = state.sportType,
                    onSelect = viewModel::setSportType,
                )
            }

            // Training type
            item {
                TrainingTypeSelector(
                    selected = state.trainingType,
                    onSelect = viewModel::setTrainingType,
                )
            }

            // Summary metrics
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    SummaryChip(Icons.Filled.Timer, formatDuration(state.estimatedDurationSeconds), Modifier.weight(1f))
                    SummaryChip(Icons.Filled.Speed, "${state.estimatedTss} TSS", Modifier.weight(1f))
                    SummaryChip(Icons.Filled.Add, "${state.blocks.size} blocks", Modifier.weight(1f))
                }
            }

            // Divider
            item {
                Text(
                    "WORKOUT BLOCKS",
                    color = TextSecondary,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 2.sp,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                )
            }

            // Block list
            itemsIndexed(state.blocks) { index, element ->
                BlockCard(
                    element = element,
                    index = index,
                    totalCount = state.blocks.size,
                    onEdit = {
                        editBlockIndex = index
                        showBlockSheet = true
                    },
                    onDelete = { viewModel.removeBlock(index) },
                    onDuplicate = { viewModel.duplicateBlock(index) },
                    onMoveUp = { if (index > 0) viewModel.moveBlock(index, index - 1) },
                    onMoveDown = { if (index < state.blocks.size - 1) viewModel.moveBlock(index, index + 1) },
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
            }

            // Add block button
            item {
                Button(
                    onClick = {
                        editBlockIndex = -1
                        showBlockSheet = true
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Primary),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    shape = RoundedCornerShape(12.dp),
                ) {
                    Icon(Icons.Filled.Add, null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Add Block")
                }
            }

            item { Spacer(Modifier.height(24.dp)) }
        }
    }

    // Block editor bottom sheet
    if (showBlockSheet) {
        val existingBlock = if (editBlockIndex >= 0 && editBlockIndex < state.blocks.size) {
            state.blocks[editBlockIndex]
        } else null

        ModalBottomSheet(
            onDismissRequest = { showBlockSheet = false },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            containerColor = SurfaceColor,
        ) {
            BlockEditorSheet(
                existingBlock = existingBlock,
                onSave = { block ->
                    if (editBlockIndex >= 0) {
                        viewModel.updateBlock(editBlockIndex, block)
                    } else {
                        viewModel.addBlock(block)
                    }
                    showBlockSheet = false
                },
                onCancel = { showBlockSheet = false },
            )
        }
    }
}

@Composable
private fun BuilderTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    keyboardType: KeyboardType = KeyboardType.Text,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        modifier = modifier.fillMaxWidth(),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = Primary,
            unfocusedBorderColor = Border,
            focusedLabelColor = Primary,
            unfocusedLabelColor = TextMuted,
            cursorColor = Primary,
            focusedTextColor = TextPrimary,
            unfocusedTextColor = TextPrimary,
        ),
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        singleLine = keyboardType != KeyboardType.Text,
    )
}

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
private fun SportTypeSelector(selected: SportType, onSelect: (SportType) -> Unit) {
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

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
private fun TrainingTypeSelector(selected: TrainingType?, onSelect: (TrainingType?) -> Unit) {
    FlowRow(
        modifier = Modifier.padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        TrainingType.entries.forEach { type ->
            val color = trainingTypeColor(type)
            FilterChip(
                selected = selected == type,
                onClick = { onSelect(type) },
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
                    selected = selected == type,
                ),
            )
        }
    }
}

@Composable
private fun SummaryChip(icon: androidx.compose.ui.graphics.vector.ImageVector, value: String, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(10.dp),
        color = SurfaceColor,
        border = BorderStroke(1.dp, Border),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
        ) {
            Icon(icon, null, tint = Primary, modifier = Modifier.size(14.dp))
            Spacer(Modifier.width(6.dp))
            Text(value, color = TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.Medium)
        }
    }
}

@Composable
private fun BlockCard(
    element: WorkoutElement,
    index: Int,
    totalCount: Int,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onDuplicate: () -> Unit,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val type = element.type ?: BlockType.STEADY
    val color = blockTypeColor(type)

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onEdit),
        shape = RoundedCornerShape(12.dp),
        color = SurfaceColor,
        border = BorderStroke(1.dp, Border),
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                // Type badge
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = color.copy(alpha = 0.15f),
                    modifier = Modifier.size(36.dp),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            type.name.take(4),
                            color = color,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }

                Spacer(Modifier.width(10.dp))

                Column(modifier = Modifier.weight(1f)) {
                    if (element.isSet) {
                        Text(
                            "Set: ${element.repetitions ?: 1}x",
                            color = Primary,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium,
                        )
                        Text(
                            "${element.elements?.size ?: 0} blocks, rest ${formatDuration(element.restDurationSeconds ?: 0)}",
                            color = TextSecondary,
                            fontSize = 12.sp,
                        )
                    } else {
                        Text(
                            element.label ?: type.name.lowercase().replaceFirstChar { it.uppercase() },
                            color = TextPrimary,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium,
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            element.durationSeconds?.let {
                                Text(formatDuration(it), color = TextSecondary, fontSize = 12.sp)
                            }
                            if (type == BlockType.RAMP) {
                                Text(
                                    "${element.intensityStart ?: 0}% → ${element.intensityEnd ?: 0}%",
                                    color = color,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold,
                                )
                            } else if (type != BlockType.PAUSE && type != BlockType.FREE) {
                                element.intensityTarget?.let {
                                    Text("$it%", color = color, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                                }
                            }
                            element.cadenceTarget?.let {
                                Text("${it}rpm", color = TextMuted, fontSize = 12.sp)
                            }
                        }
                    }
                }
            }

            // Action row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 6.dp),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (index > 0) {
                    IconButton(onClick = onMoveUp, modifier = Modifier.size(28.dp)) {
                        Icon(Icons.Filled.KeyboardArrowUp, "Move up", tint = TextMuted, modifier = Modifier.size(18.dp))
                    }
                }
                if (index < totalCount - 1) {
                    IconButton(onClick = onMoveDown, modifier = Modifier.size(28.dp)) {
                        Icon(Icons.Filled.KeyboardArrowDown, "Move down", tint = TextMuted, modifier = Modifier.size(18.dp))
                    }
                }
                IconButton(onClick = onDuplicate, modifier = Modifier.size(28.dp)) {
                    Icon(Icons.Filled.ContentCopy, "Duplicate", tint = TextMuted, modifier = Modifier.size(16.dp))
                }
                IconButton(onClick = onDelete, modifier = Modifier.size(28.dp)) {
                    Icon(Icons.Filled.Delete, "Delete", tint = Danger, modifier = Modifier.size(16.dp))
                }
            }
        }
    }
}

// ── Block Editor Bottom Sheet ──

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun BlockEditorSheet(
    existingBlock: WorkoutElement?,
    onSave: (WorkoutElement) -> Unit,
    onCancel: () -> Unit,
) {
    var blockType by remember { mutableStateOf(existingBlock?.type ?: BlockType.STEADY) }
    var durationMin by remember { mutableStateOf(((existingBlock?.durationSeconds ?: 300) / 60).toString()) }
    var durationSec by remember { mutableStateOf(((existingBlock?.durationSeconds ?: 0) % 60).toString()) }
    var intensity by remember { mutableStateOf((existingBlock?.intensityTarget ?: 75).toString()) }
    var intensityStart by remember { mutableStateOf((existingBlock?.intensityStart ?: 60).toString()) }
    var intensityEnd by remember { mutableStateOf((existingBlock?.intensityEnd ?: 90).toString()) }
    var cadence by remember { mutableStateOf(existingBlock?.cadenceTarget?.toString() ?: "") }
    var label by remember { mutableStateOf(existingBlock?.label ?: "") }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        Text(
            if (existingBlock != null) "Edit Block" else "Add Block",
            color = TextPrimary,
            fontSize = 18.sp,
            fontWeight = FontWeight.SemiBold,
        )

        Spacer(Modifier.height(12.dp))

        // Block type selector
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            BlockType.entries.forEach { type ->
                val color = blockTypeColor(type)
                val selected = blockType == type
                Surface(
                    onClick = { blockType = type },
                    shape = RoundedCornerShape(8.dp),
                    color = if (selected) color.copy(alpha = 0.2f) else Color.Transparent,
                    border = BorderStroke(1.dp, if (selected) color else Border),
                ) {
                    Text(
                        type.name.lowercase().replaceFirstChar { it.uppercase() },
                        color = if (selected) color else TextMuted,
                        fontSize = 12.sp,
                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                    )
                }
            }
        }

        Spacer(Modifier.height(12.dp))

        // Label
        BuilderTextField(
            value = label,
            onValueChange = { label = it },
            label = "Label (optional)",
        )

        Spacer(Modifier.height(8.dp))

        // Duration (min + sec)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = durationMin,
                onValueChange = { durationMin = it.filter { c -> c.isDigit() } },
                label = { Text("Min") },
                modifier = Modifier.weight(1f),
                colors = textFieldColors(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true,
            )
            OutlinedTextField(
                value = durationSec,
                onValueChange = { durationSec = it.filter { c -> c.isDigit() } },
                label = { Text("Sec") },
                modifier = Modifier.weight(1f),
                colors = textFieldColors(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true,
            )
        }

        Spacer(Modifier.height(8.dp))

        // Intensity fields (based on type)
        when (blockType) {
            BlockType.RAMP -> {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = intensityStart,
                        onValueChange = { intensityStart = it.filter { c -> c.isDigit() } },
                        label = { Text("Start %") },
                        modifier = Modifier.weight(1f),
                        colors = textFieldColors(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                    )
                    OutlinedTextField(
                        value = intensityEnd,
                        onValueChange = { intensityEnd = it.filter { c -> c.isDigit() } },
                        label = { Text("End %") },
                        modifier = Modifier.weight(1f),
                        colors = textFieldColors(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                    )
                }
            }
            BlockType.FREE, BlockType.PAUSE -> {
                // No intensity field needed
            }
            else -> {
                OutlinedTextField(
                    value = intensity,
                    onValueChange = { intensity = it.filter { c -> c.isDigit() } },
                    label = { Text("Intensity (% FTP)") },
                    modifier = Modifier.fillMaxWidth(),
                    colors = textFieldColors(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                )
            }
        }

        Spacer(Modifier.height(8.dp))

        // Cadence
        OutlinedTextField(
            value = cadence,
            onValueChange = { cadence = it.filter { c -> c.isDigit() } },
            label = { Text("Cadence RPM (optional)") },
            modifier = Modifier.fillMaxWidth(),
            colors = textFieldColors(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            singleLine = true,
        )

        Spacer(Modifier.height(16.dp))

        // Action buttons
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Button(
                onClick = onCancel,
                colors = ButtonDefaults.buttonColors(containerColor = SurfaceColor),
                border = BorderStroke(1.dp, Border),
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(10.dp),
            ) {
                Text("Cancel", color = TextSecondary)
            }
            Button(
                onClick = {
                    val totalSeconds = (durationMin.toIntOrNull() ?: 0) * 60 + (durationSec.toIntOrNull() ?: 0)
                    val block = WorkoutElement(
                        type = blockType,
                        label = label.takeIf { it.isNotBlank() },
                        durationSeconds = totalSeconds.takeIf { it > 0 },
                        intensityTarget = if (blockType != BlockType.RAMP && blockType != BlockType.FREE && blockType != BlockType.PAUSE) {
                            intensity.toIntOrNull()
                        } else null,
                        intensityStart = if (blockType == BlockType.RAMP) intensityStart.toIntOrNull() else null,
                        intensityEnd = if (blockType == BlockType.RAMP) intensityEnd.toIntOrNull() else null,
                        cadenceTarget = cadence.toIntOrNull(),
                    )
                    onSave(block)
                },
                colors = ButtonDefaults.buttonColors(containerColor = Primary),
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(10.dp),
            ) {
                Text(if (existingBlock != null) "Update" else "Add", color = Color.White)
            }
        }

        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun textFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = Primary,
    unfocusedBorderColor = Border,
    focusedLabelColor = Primary,
    unfocusedLabelColor = TextMuted,
    cursorColor = Primary,
    focusedTextColor = TextPrimary,
    unfocusedTextColor = TextPrimary,
)

private fun blockTypeColor(type: BlockType): Color = when (type) {
    BlockType.WARMUP -> BlockWarmup
    BlockType.STEADY -> BlockSteady
    BlockType.INTERVAL -> BlockInterval
    BlockType.COOLDOWN -> BlockCooldown
    BlockType.RAMP -> BlockRamp
    BlockType.FREE -> BlockFree
    BlockType.PAUSE -> BlockPause
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
