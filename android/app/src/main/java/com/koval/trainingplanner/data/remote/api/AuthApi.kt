package com.koval.trainingplanner.data.remote.api

import com.koval.trainingplanner.data.remote.dto.AuthResponse
import com.koval.trainingplanner.data.remote.dto.AuthUrlResponse
import com.koval.trainingplanner.data.remote.dto.DevLoginRequest
import com.koval.trainingplanner.data.remote.dto.UserDto
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Query

interface AuthApi {

    @POST("api/auth/dev/login")
    suspend fun devLogin(@Body request: DevLoginRequest): AuthResponse

    @GET("api/auth/strava")
    suspend fun getStravaAuthUrl(@Query("redirectUri") redirectUri: String): AuthUrlResponse

    @GET("api/auth/strava/callback")
    suspend fun stravaCallback(@Query("code") code: String): AuthResponse

    @GET("api/auth/google")
    suspend fun getGoogleAuthUrl(@Query("redirectUri") redirectUri: String): AuthUrlResponse

    @GET("api/auth/me")
    suspend fun getCurrentUser(): UserDto
}
