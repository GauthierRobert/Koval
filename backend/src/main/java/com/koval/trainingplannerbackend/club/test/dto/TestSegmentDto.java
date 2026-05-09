package com.koval.trainingplannerbackend.club.test.dto;

import com.koval.trainingplannerbackend.club.test.SegmentResultUnit;
import com.koval.trainingplannerbackend.training.model.SportType;

public record TestSegmentDto(
        String id,
        int order,
        String label,
        SportType sportType,
        Integer distanceMeters,
        Integer durationSeconds,
        SegmentResultUnit resultUnit,
        String notes
) {}
