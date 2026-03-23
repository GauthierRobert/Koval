package com.koval.trainingplanner.data.remote.api

import com.koval.trainingplanner.data.remote.dto.ClubTrainingSessionDto
import com.koval.trainingplanner.data.remote.dto.ScheduledWorkoutDto
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

interface CalendarApi {

    @GET("api/schedule")
    suspend fun getSchedule(
        @Query("start") start: String,
        @Query("end") end: String,
    ): List<ScheduledWorkoutDto>

    @POST("api/schedule/{id}/complete")
    suspend fun markCompleted(@Path("id") id: String)

    @POST("api/schedule/{id}/skip")
    suspend fun markSkipped(@Path("id") id: String)

    @DELETE("api/schedule/{id}")
    suspend fun deleteScheduledWorkout(@Path("id") id: String)

    @GET("api/schedule/club-sessions")
    suspend fun getClubSessions(
        @Query("start") start: String,
        @Query("end") end: String,
    ): List<ClubTrainingSessionDto>
}
