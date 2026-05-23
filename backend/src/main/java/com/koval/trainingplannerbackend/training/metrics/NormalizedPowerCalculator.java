package com.koval.trainingplannerbackend.training.metrics;

import java.util.List;

/**
 * Coggan's Normalized Power (NP) from a per-second power stream:
 * <ol>
 *   <li>30-second rolling mean of the power series.</li>
 *   <li>Mean of the rolling values raised to the 4th power.</li>
 *   <li>4th root of that mean — NP in watts.</li>
 * </ol>
 *
 * <p>Mirrors {@link NormalizedSpeedCalculator} but operates on integer watts,
 * since that's the resolution FIT files expose.
 */
public final class NormalizedPowerCalculator {

    private static final int ROLLING_WINDOW_SECONDS = 30;

    private NormalizedPowerCalculator() {}

    /**
     * @param watts per-second power samples in watts
     * @return Normalized Power in watts, or 0 when input is empty
     */
    public static double computeNp(List<Integer> watts) {
        if (watts == null || watts.isEmpty()) return 0;
        double[] values = new double[watts.size()];
        for (int i = 0; i < values.length; i++) values[i] = watts.get(i);

        double[] rolling = values.length >= ROLLING_WINDOW_SECONDS
                ? rollingMean(values, ROLLING_WINDOW_SECONDS)
                : values;
        double sumPow = 0;
        int count = 0;
        for (double v : rolling) {
            // Zero watts (coasting) is a legitimate sample for NP — the 4th-power
            // kernel naturally suppresses its contribution to the mean.
            double v2 = v * v;
            sumPow += v2 * v2;
            count++;
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
