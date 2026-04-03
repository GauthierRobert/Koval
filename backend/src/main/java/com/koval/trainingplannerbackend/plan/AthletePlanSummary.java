package com.koval.trainingplannerbackend.plan;

import com.koval.trainingplannerbackend.training.model.SportType;

/**
 * Summary of a training plan assigned to an athlete, including
 * progress and analytics for coach dashboard display.
 */
public record AthletePlanSummary(
        String planId,
        String planTitle,
        PlanStatus status,
        SportType sportType,
        int durationWeeks,
        int currentWeek,
        PlanProgress progress,
        PlanAnalytics analytics
) {
    public static AthletePlanSummary from(TrainingPlan plan, int currentWeek,
                                          PlanProgress progress, PlanAnalytics analytics) {
        return new AthletePlanSummary(
                plan.getId(), plan.getTitle(), plan.getStatus(),
                plan.getSportType(), plan.getDurationWeeks(),
                currentWeek, progress, analytics);
    }
}
