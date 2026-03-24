package com.koval.trainingplanner.data.repository

import com.koval.trainingplanner.data.remote.api.SessionApi
import com.koval.trainingplanner.data.remote.dto.BlockSummaryDto
import com.koval.trainingplanner.data.remote.dto.CompletedSessionDto
import com.koval.trainingplanner.data.remote.dto.PmcDataPointDto
import com.koval.trainingplanner.domain.model.BlockSummary
import com.koval.trainingplanner.domain.model.CompletedSession
import com.koval.trainingplanner.domain.model.PmcDataPoint
import com.koval.trainingplanner.domain.model.SportType
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SessionRepository @Inject constructor(
    private val sessionApi: SessionApi,
) {

    suspend fun listSessions(): List<CompletedSession> {
        return sessionApi.listSessions().map { it.toDomain() }
    }

    suspend fun getSession(id: String): CompletedSession {
        return sessionApi.getSession(id).toDomain()
    }

    suspend fun updateRpe(id: String, rpe: Int): CompletedSession {
        return sessionApi.patchSession(id, mapOf("rpe" to rpe)).toDomain()
    }

    suspend fun deleteSession(id: String) {
        sessionApi.deleteSession(id)
    }

    suspend fun getPmc(from: String, to: String): List<PmcDataPoint> {
        return sessionApi.getPmc(from, to).map { it.toDomain() }
    }

    private fun CompletedSessionDto.toDomain() = CompletedSession(
        id = id,
        trainingId = trainingId,
        title = title,
        completedAt = completedAt,
        totalDurationSeconds = totalDurationSeconds,
        avgPower = avgPower,
        avgHR = avgHR,
        avgCadence = avgCadence,
        avgSpeed = avgSpeed,
        sportType = sportType?.let { try { SportType.valueOf(it) } catch (_: Exception) { null } } ?: SportType.CYCLING,
        blockSummaries = blockSummaries?.map { it.toDomain() } ?: emptyList(),
        tss = tss,
        intensityFactor = intensityFactor,
        rpe = rpe,
        stravaActivityId = stravaActivityId,
    )

    private fun BlockSummaryDto.toDomain() = BlockSummary(
        label = label,
        type = type,
        durationSeconds = durationSeconds,
        targetPower = targetPower,
        actualPower = actualPower,
        actualCadence = actualCadence,
        actualHR = actualHR,
        distanceMeters = distanceMeters,
    )

    private fun PmcDataPointDto.toDomain() = PmcDataPoint(
        date = date,
        ctl = ctl ?: 0.0,
        atl = atl ?: 0.0,
        tsb = tsb ?: 0.0,
        dailyTss = dailyTss ?: 0.0,
        predicted = predicted ?: false,
    )
}
