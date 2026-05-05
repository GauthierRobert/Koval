package com.koval.trainingplannerbackend.club.test.dto;

import java.util.Map;

public record RecordResultRequest(
        String athleteId,
        Map<String, SegmentResultValueDto> segmentResults,
        String notes
) {}
