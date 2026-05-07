package com.koval.trainingplannerbackend.club.test.dto;

import com.koval.trainingplannerbackend.club.test.IterationStatus;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public record ClubTestIterationResponse(
        String id,
        String testId,
        String clubId,
        String label,
        LocalDate startDate,
        LocalDate endDate,
        IterationStatus status,
        LocalDateTime createdAt,
        LocalDateTime closedAt,
        List<TestSegmentDto> segments,
        List<ReferenceUpdateRuleDto> referenceUpdates,
        long resultCount
) {}
