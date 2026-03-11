package com.koval.trainingplannerbackend.pacing.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record PacingSegment(
        double startDistance,
        double endDistance,
        String discipline,
        Integer targetPower,
        String targetPace,
        Double estimatedSpeedKmh,
        double estimatedSegmentTime,
        double cumulativeFatigue,
        String nutritionSuggestion,
        double gradient,
        double elevation
) {}
