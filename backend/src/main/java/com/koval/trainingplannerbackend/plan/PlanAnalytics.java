package com.koval.trainingplannerbackend.plan;

import java.util.List;

/**
 * Aggregated analytics for a training plan: overall completion,
 * TSS adherence, and per-week breakdown.
 */
public record PlanAnalytics(
        String planId,
        String planTitle,
        PlanStatus status,
        int currentWeek,
        int totalWeeks,
        int overallCompletionPercent,
        double overallAdherencePercent,
        int totalTargetTss,
        int totalActualTss,
        List<PlanWeekAnalytics> weeklyBreakdown
) {}
