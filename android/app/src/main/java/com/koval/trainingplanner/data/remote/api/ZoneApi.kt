package com.koval.trainingplanner.data.remote.api

import com.koval.trainingplanner.data.remote.dto.ZoneSystemDto
import retrofit2.http.GET
import retrofit2.http.Path

interface ZoneApi {

    @GET("api/zones/my-zones")
    suspend fun getMyZoneSystems(): List<ZoneSystemDto>

    @GET("api/zones/{id}")
    suspend fun getZoneSystem(@Path("id") id: String): ZoneSystemDto
}
