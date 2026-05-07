package com.koval.trainingplannerbackend.club.test;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class ReferenceUpdateRule {
    private String id;
    private ReferenceTarget target;
    /** Only used when {@code target == CUSTOM}; written to {@code User.customZoneReferenceValues}. */
    private String customKey;
    private String label;
    private String unit;
    /** SpEL expression. Variables are bound as {@code #seg_<segmentId>} per segment value. */
    private String formulaExpression;
    private boolean autoApply;
}
