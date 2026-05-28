package com.koval.trainingplannerbackend.training.effectiveness;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * Per-athlete training-effectiveness report over a date window.
 *
 * <p>{@code firstHalfCurve} / {@code secondHalfCurve} hold the best mean-maximal power curves for
 * the two halves of the window; {@code curveGains} is their per-duration delta in watts (5/20/60min
 * etc.). {@code families} ranks workout families by estimated watts gained per 1000 TSS at the
 * threshold (20-minute) duration. Negative values mean the period saw a loss — possibly
 * detraining, possibly noise from low session count.
 *
 * <p>The estimator is dose-response by TSS attribution and is intentionally transparent so the UI
 * can warn when sample size is too small.
 */
public record TrainingEffectivenessReport(
        String athleteId,
        LocalDate from,
        LocalDate to,
        LocalDate splitDate,
        int sessionCount,
        double totalTss,
        Map<Integer, Double> firstHalfCurve,
        Map<Integer, Double> secondHalfCurve,
        Map<Integer, Double> curveGains,
        List<FamilyEffectiveness> families,
        String summary
) {
}
