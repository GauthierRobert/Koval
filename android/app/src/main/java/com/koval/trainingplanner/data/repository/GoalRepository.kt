package com.koval.trainingplanner.data.repository

import com.koval.trainingplanner.data.remote.api.GoalApi
import com.koval.trainingplanner.data.remote.dto.RaceGoalDto
import com.koval.trainingplanner.domain.model.RaceGoal
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GoalRepository @Inject constructor(
    private val goalApi: GoalApi,
) {

    suspend fun getGoals(): List<RaceGoal> {
        return goalApi.getGoals().map { it.toDomain() }
    }

    private fun RaceGoalDto.toDomain() = RaceGoal(
        id = id,
        title = title,
        sport = sport ?: "OTHER",
        raceDate = raceDate ?: "",
        priority = priority ?: "C",
        distance = distance,
        location = location,
        notes = notes,
        targetTime = targetTime,
    )
}
