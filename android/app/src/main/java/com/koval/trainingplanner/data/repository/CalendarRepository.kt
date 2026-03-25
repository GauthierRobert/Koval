package com.koval.trainingplanner.data.repository

import com.koval.trainingplanner.data.remote.api.CalendarApi
import com.koval.trainingplanner.data.remote.api.ClubApi
import com.koval.trainingplanner.data.remote.dto.ClubTrainingSessionDto
import com.koval.trainingplanner.data.remote.dto.ScheduledWorkoutDto
import com.koval.trainingplanner.domain.model.ClubTrainingSession
import com.koval.trainingplanner.domain.model.LinkedTraining
import com.koval.trainingplanner.domain.model.ScheduleStatus
import com.koval.trainingplanner.domain.model.ScheduledWorkout
import com.koval.trainingplanner.domain.model.SportType
import com.koval.trainingplanner.domain.model.TrainingType
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CalendarRepository @Inject constructor(
    private val calendarApi: CalendarApi,
    private val clubApi: ClubApi,
) {

    suspend fun getSchedule(start: String, end: String): List<ScheduledWorkout> {
        return calendarApi.getSchedule(start, end).map { it.toDomain() }
    }

    suspend fun getClubSessions(start: String, end: String): List<ClubTrainingSession> {
        return try {
            calendarApi.getClubSessions(start, end).map { it.toDomain() }
        } catch (_: Exception) {
            emptyList()
        }
    }

    suspend fun markCompleted(id: String) {
        calendarApi.markCompleted(id)
    }

    suspend fun markSkipped(id: String) {
        calendarApi.markSkipped(id)
    }

    suspend fun deleteScheduledWorkout(id: String) {
        calendarApi.deleteScheduledWorkout(id)
    }

    suspend fun joinSession(clubId: String, sessionId: String) {
        clubApi.joinSession(clubId, sessionId)
    }

    suspend fun leaveSession(clubId: String, sessionId: String) {
        clubApi.leaveSession(clubId, sessionId)
    }

    private fun ScheduledWorkoutDto.toDomain() = ScheduledWorkout(
        id = id,
        trainingId = trainingId,
        athleteId = athleteId,
        scheduledDate = scheduledDate,
        status = try { ScheduleStatus.valueOf(status ?: "PENDING") } catch (_: Exception) { ScheduleStatus.PENDING },
        notes = notes,
        tss = tss,
        intensityFactor = intensityFactor,
        completedAt = completedAt,
        createdAt = createdAt,
        trainingTitle = trainingTitle,
        trainingType = trainingType?.let { try { TrainingType.valueOf(it) } catch (_: Exception) { null } },
        totalDurationSeconds = totalDurationSeconds,
        sportType = sportType?.let { try { SportType.valueOf(it) } catch (_: Exception) { null } },
    )

    private fun ClubTrainingSessionDto.toDomain() = ClubTrainingSession(
        id = id,
        clubId = clubId,
        title = title ?: "Session",
        sport = sport?.let { try { SportType.valueOf(it) } catch (_: Exception) { null } },
        scheduledAt = scheduledAt ?: "",
        location = location,
        description = description,
        participantIds = participantIds ?: emptyList(),
        maxParticipants = maxParticipants,
        durationMinutes = durationMinutes,
        linkedTrainings = linkedTrainings?.map { LinkedTraining(it.trainingId, it.title, it.clubGroupId, it.clubGroupName, it.relevant ?: false) } ?: emptyList(),
        clubGroupId = clubGroupId,
        clubGroupName = clubGroupName,
        responsibleCoachId = responsibleCoachId,
        responsibleCoachName = responsibleCoachName,
        joined = joined ?: false,
        onWaitingList = onWaitingList ?: false,
        waitingListPosition = waitingListPosition ?: 0,
        cancelled = cancelled ?: false,
        cancellationReason = cancellationReason,
        clubName = clubName,
    )
}
