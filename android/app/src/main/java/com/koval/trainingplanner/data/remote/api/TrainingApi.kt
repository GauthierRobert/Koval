package com.koval.trainingplanner.data.remote.api

import com.koval.trainingplanner.data.remote.dto.TrainingDto
import retrofit2.http.GET
import retrofit2.http.Path

interface TrainingApi {

    @GET("api/trainings/{id}")
    suspend fun getTraining(@Path("id") id: String): TrainingDto
}
