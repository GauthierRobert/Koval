package com.koval.trainingplannerbackend.training.effectiveness;

/**
 * Aggregated stats for one {@link WorkoutFamily} over the report window.
 *
 * <p>{@code estimatedWattsPer1000Tss} attributes the window's power-curve gain at the threshold
 * (20-minute) duration to this family in proportion to its TSS share. It's a dose-response proxy,
 * not a causal estimate — interpret with the session count.
 */
public record FamilyEffectiveness(
        WorkoutFamily family,
        int sessionCount,
        double totalTss,
        double tssShare,
        int totalDurationSeconds,
        Double avgIntensityFactor,
        Double avgAlignment,
        Double avgRpe,
        Double estimatedWattsPer1000Tss,
        int rank
) {
}
