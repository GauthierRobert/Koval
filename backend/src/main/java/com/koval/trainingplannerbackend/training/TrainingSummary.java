package com.koval.trainingplannerbackend.training;

import java.util.List;

public record TrainingSummary(
        String id,
        String title,
        TrainingType trainingType,
        int durationMinutes,
        int blockCount,
        List<String> tags,
        TrainingVisibility visibility
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
                t.getBlocks() != null ? t.getBlocks().size() : 0,
                t.getTags(),
                t.getVisibility()
        );
    }
}
