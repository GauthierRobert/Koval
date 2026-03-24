package com.koval.trainingplanner.data.remote.dto

import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class ZoneSystemDto(
    val id: String? = null,
    val coachId: String? = null,
    val name: String? = null,
    val sportType: String? = null,
    val referenceType: String? = null,
    val referenceName: String? = null,
    val referenceUnit: String? = null,
    val zones: List<ZoneDto>? = null,
    val defaultForSport: Boolean? = null,
    val annotations: String? = null,
)

@JsonClass(generateAdapter = true)
data class ZoneDto(
    val label: String,
    val low: Int,
    val high: Int,
    val description: String? = null,
)
