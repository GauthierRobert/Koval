package com.koval.trainingplanner.domain.model

data class User(
    val id: String,
    val displayName: String,
    val profilePicture: String? = null,
    val role: UserRole,
    val ftp: Int? = null,
    val functionalThresholdPace: Int? = null,
    val criticalSwimSpeed: Int? = null,
    val needsCguAcceptance: Boolean = false,
)
