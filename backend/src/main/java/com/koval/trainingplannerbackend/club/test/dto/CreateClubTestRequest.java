package com.koval.trainingplannerbackend.club.test.dto;

import com.koval.trainingplannerbackend.club.test.RankingDirection;
import com.koval.trainingplannerbackend.club.test.RankingMetric;
import jakarta.validation.constraints.NotBlank;

import java.util.List;

public record CreateClubTestRequest(
        @NotBlank String name,
        String description,
        boolean competitionMode,
        RankingMetric rankingMetric,
        String rankingTarget,
        RankingDirection rankingDirection,
        List<TestSegmentDto> segments,
        List<ReferenceUpdateRuleDto> referenceUpdates,
        String presetId
) {}
