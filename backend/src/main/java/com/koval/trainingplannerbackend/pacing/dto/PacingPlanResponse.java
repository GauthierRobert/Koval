package com.koval.trainingplannerbackend.pacing.dto;

import java.util.List;

public record PacingPlanResponse(
        List<PacingSegment> bikeSegments,
        List<PacingSegment> runSegments,
        PacingSummary bikeSummary,
        PacingSummary runSummary
) {}
