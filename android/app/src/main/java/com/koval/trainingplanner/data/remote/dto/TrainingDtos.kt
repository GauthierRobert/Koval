package com.koval.trainingplanner.data.remote.dto

import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class TrainingDto(
    val id: String,
    val title: String,
    val description: String? = null,
    val blocks: List<WorkoutElementDto>? = null,
    val sportType: String? = null,
    val trainingType: String? = null,
    val estimatedTss: Int? = null,
    val estimatedIf: Double? = null,
    val estimatedDurationSeconds: Int? = null,
    val estimatedDistance: Int? = null,
)

@JsonClass(generateAdapter = true)
data class ReceivedTrainingDto(
    val id: String? = null,
    val trainingId: String? = null,
    val assignedByName: String? = null,
    val origin: String? = null,
    val originName: String? = null,
    val receivedAt: String? = null,
)

@JsonClass(generateAdapter = true)
data class WorkoutElementDto(
    val repetitions: Int? = null,
    val elements: List<WorkoutElementDto>? = null,
    val restDurationSeconds: Int? = null,
    val restIntensity: Int? = null,
    val type: String? = null,
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
)
