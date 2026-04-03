package com.koval.trainingplannerbackend.plan;

/**
 * Lightweight summary of the user's currently active plan,
 * designed for dashboard display.
 */
public record ActivePlanSummary(
        String planId,
        String title,
        PlanStatus status,
        int currentWeek,
        int totalWeeks,
        String weekLabel,
        int completionPercent,
        int weekWorkoutsRemaining,
        Integer weekTargetTss,
        Integer weekActualTss
) {}
