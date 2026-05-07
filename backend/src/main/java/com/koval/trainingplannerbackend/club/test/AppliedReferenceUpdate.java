package com.koval.trainingplannerbackend.club.test;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/** Embedded audit entry on {@link ClubTestResult} recording one reference value write back to {@code User}. */
@Getter
@Setter
@NoArgsConstructor
public class AppliedReferenceUpdate {
    private String ruleId;
    private ReferenceTarget target;
    private String customKey;
    private Integer previousValue;
    private Integer newValue;
    private LocalDateTime appliedAt;
    private String appliedBy;
}
