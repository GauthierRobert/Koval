package com.koval.trainingplannerbackend.club.test.dto;

import com.koval.trainingplannerbackend.club.test.ReferenceTarget;

public record ReferenceUpdateRuleDto(
        String id,
        ReferenceTarget target,
        String customKey,
        String label,
        String unit,
        String formulaExpression,
        boolean autoApply
) {}
