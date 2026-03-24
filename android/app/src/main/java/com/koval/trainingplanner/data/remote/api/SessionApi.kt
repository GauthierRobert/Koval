package com.koval.trainingplanner.data.remote.api

import com.koval.trainingplanner.data.remote.dto.CompletedSessionDto
import com.koval.trainingplanner.data.remote.dto.PmcDataPointDto
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.PATCH
import retrofit2.http.Path
import retrofit2.http.Query

interface SessionApi {

    @GET("api/sessions")
    suspend fun listSessions(): List<CompletedSessionDto>

    @GET("api/sessions/{id}")
    suspend fun getSession(@Path("id") id: String): CompletedSessionDto

    @PATCH("api/sessions/{id}")
    suspend fun patchSession(@Path("id") id: String, @Body body: Map<String, Int>): CompletedSessionDto

    @DELETE("api/sessions/{id}")
    suspend fun deleteSession(@Path("id") id: String)

    @GET("api/sessions/pmc")
    suspend fun getPmc(@Query("from") from: String, @Query("to") to: String): List<PmcDataPointDto>
}
