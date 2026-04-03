package com.koval.trainingplannerbackend.plan;

/**
 * Analytics for a single week within a training plan, comparing
 * planned targets against actual execution.
 */
public record PlanWeekAnalytics(
        int weekNumber,
        String label,
        Integer targetTss,
        int actualTss,
        int workoutsCompleted,
        int workoutsTotal,
        double adherencePercent
) {
    public static PlanWeekAnalytics of(int weekNumber, String label, Integer targetTss,
                                       int actualTss, int workoutsCompleted, int workoutsTotal) {
        double adherence = targetTss != null && targetTss > 0
                ? Math.min(100.0, 100.0 * actualTss / targetTss)
                : 0.0;
        return new PlanWeekAnalytics(weekNumber, label, targetTss, actualTss, workoutsCompleted, workoutsTotal, adherence);
    }
}
