package com.koval.trainingplannerbackend.plan;

import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
public class PlanWeek {
    private int weekNumber; // 1-based
    private String label;   // e.g. "Base Phase 1", "Build Week 3", "Recovery"
    private Integer targetTss;
    private List<PlanDay> days = new ArrayList<>();
}
