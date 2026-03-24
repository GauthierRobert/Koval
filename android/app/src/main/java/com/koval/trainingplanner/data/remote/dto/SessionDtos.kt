package com.koval.trainingplanner.data.remote.dto

import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class CompletedSessionDto(
    val id: String,
    val userId: String? = null,
    val trainingId: String? = null,
    val title: String? = null,
    val completedAt: String? = null,
    val totalDurationSeconds: Int? = null,
    val avgPower: Double? = null,
    val avgHR: Double? = null,
    val avgCadence: Double? = null,
    val avgSpeed: Double? = null,
    val sportType: String? = null,
    val blockSummaries: List<BlockSummaryDto>? = null,
    val scheduledWorkoutId: String? = null,
    val tss: Double? = null,
    val intensityFactor: Double? = null,
    val fitFileId: String? = null,
    val rpe: Int? = null,
    val syntheticCompletion: Boolean? = null,
    val stravaActivityId: String? = null,
)

@JsonClass(generateAdapter = true)
data class BlockSummaryDto(
    val label: String? = null,
    val type: String? = null,
    val durationSeconds: Int? = null,
    val targetPower: Double? = null,
    val actualPower: Double? = null,
    val actualCadence: Double? = null,
    val actualHR: Double? = null,
    val distanceMeters: Double? = null,
)

@JsonClass(generateAdapter = true)
data class PmcDataPointDto(
    val date: String,
    val ctl: Double? = null,
    val atl: Double? = null,
    val tsb: Double? = null,
    val dailyTss: Double? = null,
    val predicted: Boolean? = null,
)
