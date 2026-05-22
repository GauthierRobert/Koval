package com.koval.trainingplannerbackend.training.metrics;

import java.util.List;

/**
 * Computes Normalized Graded Pace (running) and Normalized Swimming Speed (swimming)
 * from per-second speed streams.
 *
 * <p>Both follow Coggan's Normalized Power methodology adapted to speed:
 * <ol>
 *   <li>30-second rolling mean of the speed series (smooths transient noise).</li>
 *   <li>Mean of the rolling values raised to the 4th power.</li>
 *   <li>4th root of that mean — the "normalized" speed.</li>
 * </ol>
 */
public final class NormalizedSpeedCalculator {

    private static final int ROLLING_WINDOW_SECONDS = 30;

    private NormalizedSpeedCalculator() {}

    /**
     * Normalized running speed in m/s. Altitude/grade adjustment is intentionally
     * omitted: noisy GPS/baro altitude combined with the asymmetric Minetti cost
     * polynomial systematically inflated TSS on flat runs.
     *
     * @param speedMps per-second speed samples (m/s)
     * @return normalized speed in m/s, or 0 when input is empty or yields no positive samples
     */
    public static double computeNgp(List<Double> speedMps) {
        if (speedMps == null || speedMps.isEmpty()) return 0;
        double[] s = new double[speedMps.size()];
        for (int i = 0; i < s.length; i++) s[i] = speedMps.get(i);
        return fourthPowerNormalizedAverage(s);
    }

    /**
     * Normalized Swim Speed in m/s.
     */
    public static double computeNss(List<Double> speedMps) {
        if (speedMps == null || speedMps.isEmpty()) return 0;
        double[] s = new double[speedMps.size()];
        for (int i = 0; i < s.length; i++) s[i] = speedMps.get(i);
        return fourthPowerNormalizedAverage(s);
    }

    private static double fourthPowerNormalizedAverage(double[] values) {
        int n = values.length;
        if (n == 0) return 0;
        double[] rolling = n >= ROLLING_WINDOW_SECONDS
                ? rollingMean(values, ROLLING_WINDOW_SECONDS)
                : values;
        double sumPow = 0;
        int count = 0;
        for (double v : rolling) {
            if (v > 0) {
                double v2 = v * v;
                sumPow += v2 * v2;
                count++;
            }
        }
        if (count == 0) return 0;
        return Math.pow(sumPow / count, 0.25);
    }

    private static double[] rollingMean(double[] values, int window) {
        int n = values.length;
        int outLen = n - window + 1;
        double[] out = new double[outLen];
        double sum = 0;
        for (int i = 0; i < window; i++) sum += values[i];
        out[0] = sum / window;
        for (int i = window; i < n; i++) {
            sum += values[i] - values[i - window];
            out[i - window + 1] = sum / window;
        }
        return out;
    }
}
