package com.koval.trainingplannerbackend.club.test.dto;

import java.util.List;

public record TestPresetResponse(
        String id,
        String labelKey,
        String descriptionKey,
        List<TestSegmentDto> segments,
        List<ReferenceUpdateRuleDto> referenceUpdates
) {}
