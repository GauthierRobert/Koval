package com.koval.trainingplanner.data.remote.dto

import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class DevLoginRequest(
    val userId: String,
    val displayName: String?,
    val role: String,
)

@JsonClass(generateAdapter = true)
data class AuthResponse(
    val token: String,
    val user: UserDto? = null,
)

@JsonClass(generateAdapter = true)
data class UserDto(
    val id: String,
    val displayName: String? = null,
    val profilePicture: String? = null,
    val role: String? = null,
    val ftp: Int? = null,
)

@JsonClass(generateAdapter = true)
data class AuthUrlResponse(
    val url: String,
)
