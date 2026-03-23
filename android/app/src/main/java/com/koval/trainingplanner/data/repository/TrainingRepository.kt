package com.koval.trainingplanner.data.repository

import com.koval.trainingplanner.data.remote.api.TrainingApi
import com.koval.trainingplanner.data.remote.dto.TrainingDto
import com.koval.trainingplanner.data.remote.dto.WorkoutElementDto
import com.koval.trainingplanner.domain.model.BlockType
import com.koval.trainingplanner.domain.model.SportType
import com.koval.trainingplanner.domain.model.Training
import com.koval.trainingplanner.domain.model.TrainingType
import com.koval.trainingplanner.domain.model.WorkoutElement
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TrainingRepository @Inject constructor(
    private val trainingApi: TrainingApi,
) {

    suspend fun getTraining(id: String): Training {
        return trainingApi.getTraining(id).toDomain()
    }

    private fun TrainingDto.toDomain() = Training(
        id = id,
        title = title,
        description = description,
        blocks = blocks?.map { it.toDomain() } ?: emptyList(),
        sportType = sportType?.let { try { SportType.valueOf(it) } catch (_: Exception) { null } } ?: SportType.CYCLING,
        trainingType = trainingType?.let { try { TrainingType.valueOf(it) } catch (_: Exception) { null } },
        estimatedTss = estimatedTss,
        estimatedIf = estimatedIf,
        estimatedDurationSeconds = estimatedDurationSeconds,
        estimatedDistance = estimatedDistance,
    )

    private fun WorkoutElementDto.toDomain(): WorkoutElement = WorkoutElement(
        repetitions = repetitions,
        elements = elements?.map { it.toDomain() },
        restDurationSeconds = restDurationSeconds,
        restIntensity = restIntensity,
        type = type?.let { try { BlockType.valueOf(it) } catch (_: Exception) { null } },
        durationSeconds = durationSeconds,
        distanceMeters = distanceMeters,
        label = label,
        description = description,
        intensityTarget = intensityTarget,
        intensityStart = intensityStart,
        intensityEnd = intensityEnd,
        cadenceTarget = cadenceTarget,
        zoneTarget = zoneTarget,
        zoneLabel = zoneLabel,
    )
}
