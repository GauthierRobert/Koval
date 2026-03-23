package com.koval.trainingplanner.domain.model

data class ScheduledWorkout(
    val id: String,
    val trainingId: String? = null,
    val athleteId: String? = null,
    val scheduledDate: String, // ISO YYYY-MM-DD
    val status: ScheduleStatus = ScheduleStatus.PENDING,
    val notes: String? = null,
    val tss: Double? = null,
    val intensityFactor: Double? = null,
    val completedAt: String? = null,
    val createdAt: String? = null,
    val trainingTitle: String? = null,
    val trainingType: TrainingType? = null,
    val totalDurationSeconds: Int? = null,
    val sportType: SportType? = null,
)
