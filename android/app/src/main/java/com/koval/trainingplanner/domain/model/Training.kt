package com.koval.trainingplanner.domain.model

data class Training(
    val id: String,
    val title: String,
    val description: String? = null,
    val blocks: List<WorkoutElement> = emptyList(),
    val sportType: SportType = SportType.CYCLING,
    val trainingType: TrainingType? = null,
    val estimatedTss: Int? = null,
    val estimatedIf: Double? = null,
    val estimatedDurationSeconds: Int? = null,
    val estimatedDistance: Int? = null,
)

data class ReceivedTraining(
    val id: String,
    val trainingId: String,
    val assignedByName: String?,
    val origin: String,
    val originName: String?,
)

data class WorkoutElement(
    // Set fields
    val repetitions: Int? = null,
    val elements: List<WorkoutElement>? = null,
    val restDurationSeconds: Int? = null,
    val restIntensity: Int? = null,
    // Leaf fields
    val type: BlockType? = null,
    val durationSeconds: Int? = null,
    val distanceMeters: Int? = null,
    val label: String? = null,
    val description: String? = null,
    val intensityTarget: Int? = null,
    val intensityStart: Int? = null,
    val intensityEnd: Int? = null,
    val cadenceTarget: Int? = null,
    val zoneTarget: String? = null,
    val zoneLabel: String? = null,
) {
    val isSet: Boolean get() = !elements.isNullOrEmpty()
}
