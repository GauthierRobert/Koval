package com.koval.trainingplannerbackend.plan;

import lombok.Getter;
import lombok.Setter;

import java.time.DayOfWeek;
import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
public class PlanDay {
    private DayOfWeek dayOfWeek;
    private List<String> trainingIds = new ArrayList<>();
    private String notes;
    private List<String> scheduledWorkoutIds = new ArrayList<>(); // populated after activation, parallel to trainingIds
}
