package com.koval.trainingplannerbackend.coach;

import com.koval.trainingplannerbackend.club.session.ClubTrainingSession;
import com.koval.trainingplannerbackend.training.model.SportType;
import com.koval.trainingplannerbackend.training.model.Training;
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
        SportType sportType,
        String sessionId,
        boolean isClubSession,
        String clubName,
        String clubGroupName,
        // Plan context fields
        String planId,
        String planTitle,
        Integer weekNumber,
        String weekLabel) {

    public static ScheduledWorkoutResponse from(ScheduledWorkout sw, String trainingTitle, TrainingType trainingType,
            Integer totalDurationSeconds, SportType sportType, Integer estimatedTss, Double estimatedIf,
            String planId, String planTitle, Integer weekNumber, String weekLabel) {
        return new ScheduledWorkoutResponse(
                sw.getId(),
                sw.getTrainingId(),
                sw.getAthleteId(),
                sw.getAssignedBy(),
                sw.getScheduledDate(),
                sw.getStatus(),
                sw.getNotes(),
                sw.getTss() != null ? sw.getTss() : estimatedTss,
                sw.getIntensityFactor() != null ? sw.getIntensityFactor() : estimatedIf,
                sw.getCompletedAt(),
                sw.getCreatedAt(),
                trainingTitle,
                trainingType,
                totalDurationSeconds,
                sportType,
                sw.getSessionId(),
                false, null, null,
                planId, planTitle, weekNumber, weekLabel);
    }

    public static ScheduledWorkoutResponse fromClubSession(
            ClubTrainingSession session, String clubName, String clubGroupName,
            Training linkedTraining) {
        String title;
        Integer tss = null;
        Double intensityFactor = null;
        Integer duration;
        SportType sport;
        TrainingType trainingType = null;

        if (linkedTraining != null) {
            title = linkedTraining.getTitle();
            tss = linkedTraining.getEstimatedTss();
            intensityFactor = linkedTraining.getEstimatedIf();
            duration = linkedTraining.getEstimatedDurationSeconds();
            sport = linkedTraining.getSportType();
            trainingType = linkedTraining.getTrainingType();
        } else {
            title = session.getTitle();
            duration = session.getDurationMinutes() != null ? session.getDurationMinutes() * 60 : null;
            sport = SportType.fromStringOrNull(session.getSport());
        }

        return new ScheduledWorkoutResponse(
                session.getId(),
                session.getLinkedTrainingId(),
                null,
                session.getResponsibleCoachId(),
                session.getScheduledAt() != null ? session.getScheduledAt().toLocalDate() : null,
                ScheduleStatus.PENDING,
                session.getDescription(),
                tss,
                intensityFactor,
                null,
                session.getCreatedAt(),
                title,
                trainingType,
                duration,
                sport,
                null,
                true, clubName, clubGroupName,
                null, null, null, null);
    }

}
