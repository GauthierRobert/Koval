package com.koval.trainingplannerbackend.club.test.dto;

import com.koval.trainingplannerbackend.club.test.RankingDirection;
import com.koval.trainingplannerbackend.club.test.RankingMetric;

import java.time.LocalDateTime;
import java.util.List;

public record ClubTestDetailResponse(
        String id,
        String clubId,
        String name,
        String description,
        String createdBy,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        boolean competitionMode,
        RankingMetric rankingMetric,
        String rankingTarget,
        RankingDirection rankingDirection,
        List<TestSegmentDto> segments,
        List<ReferenceUpdateRuleDto> referenceUpdates,
        String currentIterationId,
        boolean archived,
        boolean hasResults
) {}
