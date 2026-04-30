package com.koval.trainingplannerbackend.training.metrics;

import java.util.List;

/**
 * Computes Normalized Graded Pace (running) and Normalized Swimming Speed (swimming)
 * from per-second speed/altitude streams.
 *
 * <p>Both follow Coggan's Normalized Power methodology adapted to speed:
 * <ol>
 *   <li>30-second rolling mean of the speed series (smooths transient noise).</li>
 *   <li>Mean of the rolling values raised to the 4th power.</li>
 *   <li>4th root of that mean — the "normalized" speed.</li>
 * </ol>
 * For running, each per-second sample is first converted to flat-equivalent speed using
 * the Minetti et al. (2002) energy-cost polynomial so hills are accounted for.
 */
public final class NormalizedSpeedCalculator {

    private static final int ROLLING_WINDOW_SECONDS = 30;
    private static final int GRADE_WINDOW_SECONDS = 30;
    private static final double MIN_DX_METERS = 5.0;
    private static final double GRADE_CLAMP = 0.30;
    private static final double FLAT_COST = 3.6; // J/kg/m, Minetti C(0)

    private NormalizedSpeedCalculator() {}

    /**
     * Normalized Graded Pace as a flat-equivalent speed in m/s.
     *
     * @param speedMps       per-second speed samples (m/s)
     * @param altitudeMeters per-second altitude samples (m), aligned with speed; NaN allowed
     * @return NGP in m/s, or 0 when input is empty or yields no positive samples
     */
    public static double computeNgp(List<Double> speedMps, List<Double> altitudeMeters) {
        if (speedMps == null || speedMps.isEmpty()) return 0;
        double[] equiv = gradeAdjustedSpeed(speedMps, altitudeMeters);
        return fourthPowerNormalizedAverage(equiv);
    }

    /**
     * Normalized Swim Speed in m/s. No grade adjustment; pool/open-water swims are flat.
     */
    public static double computeNss(List<Double> speedMps) {
        if (speedMps == null || speedMps.isEmpty()) return 0;
        double[] s = new double[speedMps.size()];
        for (int i = 0; i < s.length; i++) s[i] = speedMps.get(i);
        return fourthPowerNormalizedAverage(s);
    }

    /**
     * Compute per-sample flat-equivalent speed by adjusting for grade.
     *
     * <p>Grade is taken as the slope over a {@value #GRADE_WINDOW_SECONDS}-second window
     * rather than between consecutive samples. GPS/barometric altitude has ~3–5m RMS
     * noise; differentiating it per-second turns even flat terrain into apparent ±30%
     * grades, and Minetti's cost polynomial is asymmetric (uphill costs more than
     * downhill saves), so per-sample noise systematically inflates the flat-equivalent
     * speed. Windowing the grade computation averages noise out before the polynomial.
     */
    private static double[] gradeAdjustedSpeed(List<Double> speed, List<Double> altitude) {
        int n = speed.size();
        double[] out = new double[n];
        boolean hasAltitude = altitude != null && altitude.size() == n;
        for (int i = 0; i < n; i++) {
            double v = speed.get(i);
            if (!hasAltitude || v <= 0) {
                out[i] = v;
                continue;
            }
            double grade = windowedGrade(speed, altitude, i);
            out[i] = v * minettiCostRatio(grade);
        }
        return out;
    }

    private static double windowedGrade(List<Double> speed, List<Double> altitude, int i) {
        int start = Math.max(0, i - GRADE_WINDOW_SECONDS);
        double a0 = altitude.get(start);
        double a1 = altitude.get(i);
        if (Double.isNaN(a0) || Double.isNaN(a1)) return 0;
        double dx = 0;
        for (int j = start + 1; j <= i; j++) {
            double s = speed.get(j);
            if (s > 0) dx += s; // 1s per sample
        }
        if (dx < MIN_DX_METERS) return 0;
        return clamp((a1 - a0) / dx, -GRADE_CLAMP, GRADE_CLAMP);
    }

    /**
     * Minetti et al. (2002) running energy cost C(i) divided by C(0) = 3.6 J/kg/m, where
     * i is the gradient (rise/run). Equals 1.0 on the flat, &gt;1 uphill, &lt;1 downhill
     * (within the natural range — at very steep negative grades the polynomial bends back up).
     */
    private static double minettiCostRatio(double grade) {
        double i = grade;
        double i2 = i * i;
        double i3 = i2 * i;
        double i4 = i2 * i2;
        double i5 = i4 * i;
        double c = 155.4 * i5 - 30.4 * i4 - 43.3 * i3 + 46.3 * i2 + 19.5 * i + FLAT_COST;
        return c / FLAT_COST;
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

    private static double clamp(double v, double lo, double hi) {
        return Math.max(lo, Math.min(hi, v));
    }
}
