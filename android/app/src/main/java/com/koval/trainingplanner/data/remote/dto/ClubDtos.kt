package com.koval.trainingplanner.data.remote.dto

import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class ClubTrainingSessionDto(
    val id: String,
    val clubId: String,
    val title: String? = null,
    val sport: String? = null,
    val scheduledAt: String? = null,
    val location: String? = null,
    val description: String? = null,
    val participantIds: List<String>? = null,
    val maxParticipants: Int? = null,
    val durationMinutes: Int? = null,
    val linkedTrainings: List<LinkedTrainingDto>? = null,
    val clubGroupId: String? = null,
    val clubGroupName: String? = null,
    val responsibleCoachId: String? = null,
    val responsibleCoachName: String? = null,
    val joined: Boolean? = null,
    val onWaitingList: Boolean? = null,
    val waitingListPosition: Int? = null,
    val cancelled: Boolean? = null,
    val cancellationReason: String? = null,
    val clubName: String? = null,
)

@JsonClass(generateAdapter = true)
data class LinkedTrainingDto(
    val trainingId: String,
    val title: String? = null,
    val clubGroupId: String? = null,
    val clubGroupName: String? = null,
    val relevant: Boolean? = null,
)

@JsonClass(generateAdapter = true)
data class NotificationTokenRequest(
    val token: String,
)

@JsonClass(generateAdapter = true)
data class RedeemInviteRequest(
    val code: String,
)

@JsonClass(generateAdapter = true)
data class RedeemInviteResponse(
    val type: String? = null,
    val message: String? = null,
)
