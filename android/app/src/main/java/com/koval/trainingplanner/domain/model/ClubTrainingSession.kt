package com.koval.trainingplanner.domain.model

data class ClubTrainingSession(
    val id: String,
    val clubId: String,
    val title: String,
    val sport: SportType? = null,
    val scheduledAt: String, // ISO datetime
    val location: String? = null,
    val description: String? = null,
    val participantIds: List<String> = emptyList(),
    val waitingList: List<String> = emptyList(),
    val maxParticipants: Int? = null,
    val durationMinutes: Int? = null,
    val linkedTrainings: List<LinkedTraining> = emptyList(),
    val clubGroupId: String? = null,
    val clubGroupName: String? = null,
    val responsibleCoachId: String? = null,
    val responsibleCoachName: String? = null,
    val cancelled: Boolean = false,
    val cancellationReason: String? = null,
    val clubName: String? = null,
)

data class LinkedTraining(
    val trainingId: String,
    val title: String? = null,
    val clubGroupId: String? = null,
)
