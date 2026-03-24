package com.koval.trainingplanner.data.repository

import com.koval.trainingplanner.data.remote.api.ZoneApi
import com.koval.trainingplanner.data.remote.dto.ZoneDto
import com.koval.trainingplanner.data.remote.dto.ZoneSystemDto
import com.koval.trainingplanner.domain.model.SportType
import com.koval.trainingplanner.domain.model.Zone
import com.koval.trainingplanner.domain.model.ZoneSystem
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ZoneRepository @Inject constructor(
    private val zoneApi: ZoneApi,
) {

    suspend fun getMyZoneSystems(): List<ZoneSystem> {
        return zoneApi.getMyZoneSystems().map { it.toDomain() }
    }

    suspend fun getZoneSystem(id: String): ZoneSystem {
        return zoneApi.getZoneSystem(id).toDomain()
    }

    private fun ZoneSystemDto.toDomain() = ZoneSystem(
        id = id ?: "",
        name = name ?: "Unnamed",
        sportType = sportType?.let {
            try { SportType.valueOf(it) } catch (_: Exception) { null }
        } ?: SportType.CYCLING,
        referenceType = referenceType ?: "CUSTOM",
        referenceName = referenceName,
        referenceUnit = referenceUnit,
        zones = zones?.map { it.toDomain() } ?: emptyList(),
        defaultForSport = defaultForSport ?: false,
    )

    private fun ZoneDto.toDomain() = Zone(
        label = label,
        low = low,
        high = high,
        description = description,
    )
}
