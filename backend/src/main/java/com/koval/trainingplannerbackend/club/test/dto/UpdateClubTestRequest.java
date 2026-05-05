package com.koval.trainingplannerbackend.club.test.dto;

import com.koval.trainingplannerbackend.club.test.RankingDirection;
import com.koval.trainingplannerbackend.club.test.RankingMetric;

import java.util.List;

public record UpdateClubTestRequest(
        String name,
        String description,
        Boolean competitionMode,
        RankingMetric rankingMetric,
        String rankingTarget,
        RankingDirection rankingDirection,
        List<TestSegmentDto> segments,
        List<ReferenceUpdateRuleDto> referenceUpdates
) {}
