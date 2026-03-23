package com.koval.trainingplanner.data.remote.dto

import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class ScheduledWorkoutDto(
    val id: String,
    val trainingId: String? = null,
    val athleteId: String? = null,
    val scheduledDate: String,
    val status: String? = null,
    val notes: String? = null,
    val tss: Double? = null,
    val intensityFactor: Double? = null,
    val completedAt: String? = null,
    val createdAt: String? = null,
    val trainingTitle: String? = null,
    val trainingType: String? = null,
    val totalDurationSeconds: Int? = null,
    val sportType: String? = null,
)
