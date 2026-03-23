package com.koval.trainingplanner.data.remote.api

import com.koval.trainingplanner.data.remote.dto.RaceGoalDto
import retrofit2.http.GET

interface GoalApi {

    @GET("api/goals")
    suspend fun getGoals(): List<RaceGoalDto>
}
