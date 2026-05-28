package com.koval.trainingplannerbackend.training.history.compare;

import java.time.LocalDateTime;

/** Candidate session returned by the similarity ranking endpoint. */
public record SimilarSessionDto(
        String id,
        String title,
        String sportType,
        LocalDateTime completedAt,
        int totalDurationSeconds,
        Double tss,
        Double intensityFactor,
        Double normalizedPower,
        Double avgPower,
        Double totalDistance,
        int similarityPercent) {
}
