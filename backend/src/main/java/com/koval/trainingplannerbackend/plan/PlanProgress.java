package com.koval.trainingplannerbackend.plan;

public record PlanProgress(
        String planId,
        int totalWorkouts,
        int completedWorkouts,
        int skippedWorkouts,
        int pendingWorkouts,
        int completionPercent,
        int currentWeek
) {
    public static PlanProgress of(String planId, int total, int completed, int skipped, int pending, int currentWeek) {
        int percent = total > 0 ? (int) Math.round(100.0 * completed / total) : 0;
        return new PlanProgress(planId, total, completed, skipped, pending, percent, currentWeek);
    }
}
