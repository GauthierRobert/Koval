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
}
