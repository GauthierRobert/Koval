package com.koval.trainingplanner.data.remote.dto

import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class RaceGoalDto(
    val id: String,
    val title: String,
    val sport: String? = null,
    val raceDate: String? = null,
    val priority: String? = null,
    val distance: String? = null,
    val location: String? = null,
    val notes: String? = null,
    val targetTime: String? = null,
)
