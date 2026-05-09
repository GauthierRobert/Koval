package com.koval.trainingplannerbackend.club.test.dto;

import com.koval.trainingplannerbackend.club.test.ReferenceTarget;

import java.time.LocalDateTime;

public record AppliedReferenceUpdateDto(
        String ruleId,
        ReferenceTarget target,
        String customKey,
        Integer previousValue,
        Integer newValue,
        LocalDateTime appliedAt,
        String appliedBy
) {}
