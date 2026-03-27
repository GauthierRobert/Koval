package com.koval.trainingplannerbackend.training.tools;

import com.koval.trainingplannerbackend.training.model.Training;
import com.koval.trainingplannerbackend.training.model.TrainingType;
import com.koval.trainingplannerbackend.training.model.WorkoutElementFlattener;

import java.util.List;

/** Lightweight training summary returned by AI tool operations to minimize token usage. */
public record TrainingSummary(
        String id,
        String title,
        TrainingType trainingType,
        int durationMinutes,
        int distance,
        int blockCount,
        List<String> tags
) {
    public static TrainingSummary from(Training t) {
        int durationMin = t.getEstimatedDurationSeconds() != null
                ? t.getEstimatedDurationSeconds() / 60
                : 0;
        return new TrainingSummary(
                t.getId(),
                t.getTitle(),
                t.getTrainingType(),
                durationMin,
                t.getEstimatedDistance() == null ? 0 : t.getEstimatedDistance(),
                t.getBlocks() != null ? WorkoutElementFlattener.flatten(t.getBlocks()).size() : 0,
                t.getGroupIds()
        );
    }
}
