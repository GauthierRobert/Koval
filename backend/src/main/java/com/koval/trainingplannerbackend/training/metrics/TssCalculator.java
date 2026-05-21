package com.koval.trainingplannerbackend.training.metrics;

public final class TssCalculator {

    private TssCalculator() {}

    /**
     * Computes Training Stress Score from duration and intensity factor.
     * TSS = durationHours × IF² × 100
     *
     * @param durationSeconds workout duration in seconds
     * @param intensityFactor ratio of actual effort to threshold (e.g. 0.85 = 85% FTP)
     */
    public static double computeTss(double durationSeconds, double intensityFactor) {
        double durationHours = durationSeconds / 3600.0;
        return durationHours * intensityFactor * intensityFactor * 100.0;
    }

    /**
     * Computes Intensity Factor from TSS and duration.
     * IF = sqrt(TSS / (durationHours × 100))
     *
     * @param tss the training stress score
     * @param durationSeconds workout duration in seconds
     */
    public static double computeIf(double tss, double durationSeconds) {
        double durationHours = durationSeconds / 3600.0;
        if (durationHours <= 0) return 0.0;
        return Math.sqrt(tss / (durationHours * 100.0));
    }

    /**
     * Maps a 1–10 RPE (Borg CR10) to an intensity factor using the Coggan/Allen
     * perceived-exertion table. The linear {@code rpe/10} mapping previously used
     * understates easy/recovery work — RPE 2 over an hour produced TSS=4, which is
     * nowhere near the metabolic cost of an hour of zone-1 spinning.
     *
     * <p>Reference curve (Training & Racing with a Power Meter, Allen/Coggan):
     * <pre>
     * RPE  1  2  3  4   5   6   7   8   9   10
     * IF  .50 .55 .60 .65 .75 .80 .85 .90 .95 1.05
     * </pre>
     */
    public static double intensityFactorFromRpe(int rpe) {
        int clamped = Math.max(1, Math.min(10, rpe));
        return switch (clamped) {
            case 1 -> 0.50;
            case 2 -> 0.55;
            case 3 -> 0.60;
            case 4 -> 0.65;
            case 5 -> 0.75;
            case 6 -> 0.80;
            case 7 -> 0.85;
            case 8 -> 0.90;
            case 9 -> 0.95;
            default -> 1.05; // RPE 10 — all-out, supra-threshold
        };
    }
}
