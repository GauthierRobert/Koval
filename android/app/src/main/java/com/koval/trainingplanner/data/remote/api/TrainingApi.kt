package com.koval.trainingplanner.data.remote.api

import com.koval.trainingplanner.data.remote.dto.ReceivedTrainingDto
import com.koval.trainingplanner.data.remote.dto.TrainingDto
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.DELETE
import retrofit2.http.Path

interface TrainingApi {

    @GET("api/trainings")
    suspend fun listTrainings(): List<TrainingDto>

    @GET("api/trainings/{id}")
    suspend fun getTraining(@Path("id") id: String): TrainingDto

    @GET("api/trainings/club-trainings")
    suspend fun listClubTrainings(): List<TrainingDto>

    @GET("api/trainings/received")
    suspend fun listReceivedTrainings(): List<ReceivedTrainingDto>

    @POST("api/trainings")
    suspend fun createTraining(@Body training: TrainingDto): TrainingDto

    @PUT("api/trainings/{id}")
    suspend fun updateTraining(@Path("id") id: String, @Body training: TrainingDto): TrainingDto

    @DELETE("api/trainings/{id}")
    suspend fun deleteTraining(@Path("id") id: String)
}
