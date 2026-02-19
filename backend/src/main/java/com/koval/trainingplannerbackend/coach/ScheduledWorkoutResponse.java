package com.koval.trainingplannerbackend.coach;

import com.koval.trainingplannerbackend.training.model.SportType;
import com.koval.trainingplannerbackend.training.model.TrainingType;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Response DTO that enriches ScheduledWorkout with training metadata.
 */
public record ScheduledWorkoutResponse(
        String id,
        String trainingId,
        String athleteId,
        String assignedBy,
        LocalDate scheduledDate,
        ScheduleStatus status,
        String notes,
        Integer tss,
        Double intensityFactor,
        LocalDateTime completedAt,
        LocalDateTime createdAt,
        // Enriched fields
        String trainingTitle,
        TrainingType trainingType,
        Integer totalDurationSeconds,
        SportType sportType) {

    public static ScheduledWorkoutResponse from(ScheduledWorkout sw, String trainingTitle, TrainingType trainingType, Integer totalDurationSeconds, SportType sportType) {
        return new ScheduledWorkoutResponse(
                sw.getId(),
                sw.getTrainingId(),
                sw.getAthleteId(),
                sw.getAssignedBy(),
                sw.getScheduledDate(),
                sw.getStatus(),
                sw.getNotes(),
                sw.getTss(),
                sw.getIntensityFactor(),
                sw.getCompletedAt(),
                sw.getCreatedAt(),
                trainingTitle,
                trainingType,
                totalDurationSeconds,
                sportType);
    }
}
