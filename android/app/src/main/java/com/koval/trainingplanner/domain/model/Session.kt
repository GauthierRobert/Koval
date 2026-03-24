package com.koval.trainingplanner.domain.model

data class CompletedSession(
    val id: String,
    val trainingId: String? = null,
    val title: String? = null,
    val completedAt: String? = null,
    val totalDurationSeconds: Int? = null,
    val avgPower: Double? = null,
    val avgHR: Double? = null,
    val avgCadence: Double? = null,
    val avgSpeed: Double? = null,
    val sportType: SportType = SportType.CYCLING,
    val blockSummaries: List<BlockSummary> = emptyList(),
    val tss: Double? = null,
    val intensityFactor: Double? = null,
    val rpe: Int? = null,
    val stravaActivityId: String? = null,
)

data class BlockSummary(
    val label: String? = null,
    val type: String? = null,
    val durationSeconds: Int? = null,
    val targetPower: Double? = null,
    val actualPower: Double? = null,
    val actualCadence: Double? = null,
    val actualHR: Double? = null,
    val distanceMeters: Double? = null,
)

data class PmcDataPoint(
    val date: String,
    val ctl: Double = 0.0,
    val atl: Double = 0.0,
    val tsb: Double = 0.0,
    val dailyTss: Double = 0.0,
    val predicted: Boolean = false,
)
