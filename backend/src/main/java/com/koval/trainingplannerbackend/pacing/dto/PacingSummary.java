package com.koval.trainingplannerbackend.pacing.dto;

public record PacingSummary(
        double totalDistance,
        double estimatedTotalTime,
        Integer averagePower,
        String averagePace,
        int totalCalories,
        String nutritionPlan,
        String targetBasis,
        Integer computedTarget
) {}
