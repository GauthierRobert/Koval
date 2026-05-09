package com.koval.trainingplannerbackend.club.test.dto;

import java.time.LocalDateTime;

public record ClubTestSummaryResponse(
        String id,
        String clubId,
        String name,
        String description,
        boolean competitionMode,
        boolean archived,
        int segmentCount,
        int ruleCount,
        long iterationCount,
        String currentIterationId,
        String currentIterationLabel,
        LocalDateTime createdAt
) {}
