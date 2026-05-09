package com.koval.trainingplannerbackend.club.test.dto;

import com.koval.trainingplannerbackend.club.test.SegmentResultUnit;

public record SegmentResultValueDto(
        double value,
        SegmentResultUnit unit,
        String completedSessionId
) {}
