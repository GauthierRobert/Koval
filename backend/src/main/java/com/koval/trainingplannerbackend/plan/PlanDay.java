package com.koval.trainingplannerbackend.plan;

import lombok.Getter;
import lombok.Setter;

import java.time.DayOfWeek;

@Getter
@Setter
public class PlanDay {
    private DayOfWeek dayOfWeek;
    private String trainingId;
    private String notes;
    private String scheduledWorkoutId; // populated after activation
}
