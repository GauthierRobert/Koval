package com.koval.trainingplanner.domain.model

data class ZoneSystem(
    val id: String,
    val name: String,
    val sportType: SportType,
    val referenceType: String,
    val referenceName: String?,
    val referenceUnit: String?,
    val zones: List<Zone>,
    val defaultForSport: Boolean,
)

data class Zone(
    val label: String,
    val low: Int,
    val high: Int,
    val description: String?,
)
