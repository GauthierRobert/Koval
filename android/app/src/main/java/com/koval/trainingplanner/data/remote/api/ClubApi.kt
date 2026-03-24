package com.koval.trainingplanner.data.remote.api

import com.koval.trainingplanner.data.remote.dto.ClubTrainingSessionDto
import com.koval.trainingplanner.data.remote.dto.RedeemInviteRequest
import com.koval.trainingplanner.data.remote.dto.RedeemInviteResponse
import retrofit2.http.Body
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

    @POST("api/coach/redeem-invite")
    suspend fun redeemInvite(@Body request: RedeemInviteRequest): RedeemInviteResponse
}
