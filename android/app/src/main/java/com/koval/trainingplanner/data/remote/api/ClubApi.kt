package com.koval.trainingplanner.data.remote.api

import com.koval.trainingplanner.data.remote.dto.ClubTrainingSessionDto
import retrofit2.http.DELETE
import retrofit2.http.POST
import retrofit2.http.Path

interface ClubApi {

    @POST("api/clubs/{clubId}/sessions/{sessionId}/join")
    suspend fun joinSession(
        @Path("clubId") clubId: String,
        @Path("sessionId") sessionId: String,
    )

    @DELETE("api/clubs/{clubId}/sessions/{sessionId}/join")
    suspend fun leaveSession(
        @Path("clubId") clubId: String,
        @Path("sessionId") sessionId: String,
    )
}
