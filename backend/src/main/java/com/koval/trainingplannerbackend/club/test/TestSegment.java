package com.koval.trainingplannerbackend.club.test;

import com.koval.trainingplannerbackend.training.model.SportType;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class TestSegment {
    /** Stable UUID — referenced by formula variables and result keys; safe to keep across iterations. */
    private String id;
    private int order;
    private String label;
    private SportType sportType;
    private Integer distanceMeters;
    private Integer durationSeconds;
    private SegmentResultUnit resultUnit;
    private String notes;
}
