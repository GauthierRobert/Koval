package com.koval.trainingplanner.domain.model

data class RaceGoal(
    val id: String,
    val title: String,
    val sport: String, // CYCLING, RUNNING, SWIMMING, TRIATHLON, OTHER
    val raceDate: String, // YYYY-MM-DD
    val priority: String, // A, B, C
    val distance: String? = null,
    val location: String? = null,
    val notes: String? = null,
    val targetTime: String? = null,
)
