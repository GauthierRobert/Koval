package com.koval.trainingplanner.domain.model

data class User(
    val id: String,
    val displayName: String,
    val profilePicture: String? = null,
    val role: UserRole,
    val ftp: Int? = null,
    val functionalThresholdPace: Int? = null,
    val criticalSwimSpeed: Int? = null,
    val vo2maxPace: Int? = null,
    val pace5k: Int? = null,
    val pace10k: Int? = null,
    val paceHalfMarathon: Int? = null,
    val paceMarathon: Int? = null,
    val needsCguAcceptance: Boolean = false,
)
