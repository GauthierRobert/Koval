package com.koval.trainingplannerbackend.training.tools;

import com.koval.trainingplannerbackend.training.model.Training;
import com.koval.trainingplannerbackend.training.model.TrainingType;
import com.koval.trainingplannerbackend.training.model.WorkoutBlock;

import java.util.List;

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
        int totalSeconds = t.getBlocks() != null
                ? t.getBlocks().stream().mapToInt(WorkoutBlock::durationSeconds).sum()
                : 0;
        return new TrainingSummary(
                t.getId(),
                t.getTitle(),
                t.getTrainingType(),
                totalSeconds / 60,
                t.getEstimatedDistance() == null ? 0 : t.getEstimatedDistance(),
                t.getBlocks() != null ? t.getBlocks().size() : 0,
                t.getTags()
        );
    }
}
