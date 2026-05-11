package com.koval.trainingplannerbackend.training;

import com.koval.trainingplannerbackend.training.model.SportType;
import com.koval.trainingplannerbackend.training.model.Training;
import com.koval.trainingplannerbackend.training.model.TrainingType;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Lightweight projection of {@link Training} for list endpoints.
 *
 * <p>Mirrors all common fields except {@code blocks} (the workout-element tree
 * is the bulk of a training document and is only needed on the detail page).
 * Returning summaries on listing endpoints cuts JSON payloads from 50-100KB
 * per item down to a few hundred bytes.
 */
public record TrainingSummary(
        String id,
        String title,
        String description,
        SportType sportType,
        TrainingType trainingType,
        Integer estimatedTss,
        Double estimatedIf,
        Integer estimatedDurationSeconds,
        Integer estimatedDistance,
        String zoneSystemId,
        String createdBy,
        LocalDateTime createdAt,
        List<String> clubIds,
        List<String> clubGroupIds,
        List<String> groupIds
) {

    public static TrainingSummary from(Training t) {
        return new TrainingSummary(
                t.getId(),
                t.getTitle(),
                t.getDescription(),
                t.getSportType(),
                t.getTrainingType(),
                t.getEstimatedTss(),
                t.getEstimatedIf(),
                t.getEstimatedDurationSeconds(),
                t.getEstimatedDistance(),
                t.getZoneSystemId(),
                t.getCreatedBy(),
                t.getCreatedAt(),
                t.getClubIds(),
                t.getClubGroupIds(),
                t.getGroupIds());
    }
}
