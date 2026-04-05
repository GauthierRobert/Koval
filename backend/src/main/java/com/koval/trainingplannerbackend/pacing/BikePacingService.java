package com.koval.trainingplannerbackend.pacing;

import com.koval.trainingplannerbackend.pacing.dto.AthleteProfile;
import com.koval.trainingplannerbackend.pacing.dto.PacingSegment;
import com.koval.trainingplannerbackend.pacing.dto.PacingSummary;
import com.koval.trainingplannerbackend.pacing.gpx.CourseSegment;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
class BikePacingService {

    private final BikeSpeedService speedService;

    BikePacingService(BikeSpeedService speedService) {
        this.speedService = speedService;
    }

    // Physics constants
    private static final double BIKE_WEIGHT_KG = 8.0;
    private static final double MAX_SPEED_MS = 22.0;
    private static final double MIN_SPEED_MS = 2.0;

    /** Gradient response: maps road gradient (decimal) to power multiplier.
     *  Downhill → less power needed; steep climb → more power needed. */
    private static final double[] GRAD_KNOTS   = {-0.08, -0.03, 0.0, 0.05, 0.12};
    private static final double[] GRAD_FACTORS = { 0.60,  0.85, 1.0, 1.15, 1.30};

    /** Power bounds: 60%–130% of GTP prevents unrealistic extremes. */
    private static final double MAX_POWER_RATIO = 1.30;
    private static final double MIN_POWER_RATIO = 0.60;

    /** Budget balancing: Newton-like iteration to match time-weighted avg power = GTP. */
    private static final int BUDGET_MAX_ITERATIONS = 5;
    private static final double BUDGET_CONVERGENCE = 0.001;

    /** Transition smoothing: caps segment-to-segment power delta at 10%. */
    private static final double MAX_POWER_DELTA_RATIO = 0.10;

    // Aero correction
    private static final double AERO_BONUS_SCALE = 0.08;
    private static final double V_LOW = 4.0;
    private static final double V_HIGH = 12.0;
    private static final double CDA_REFERENCE = 0.32;

    /** Fatigue: TSS-based model; denominator 500 normalizes fatigue to 0–1 range. */
    private static final double FATIGUE_DENOMINATOR = 500.0;
    private static final int NUTRITION_INTERVAL_MIN = 20;
    private static final double CARBS_PER_HOUR = 80.0;
    private static final double KCAL_FACTOR = 3.6;

    /** Smart target: standard cycling power-duration curve — distance (m) → % FTP. */
    private static final double[] DIST_POINTS   = {20_000, 40_000, 90_000, 180_000};
    private static final double[] FTP_PCTS      = {0.95,  0.90,  0.82,   0.72};
    private static final double[] TRI_FTP_PCTS  = {0.88,  0.83,  0.77,   0.68};

    record BikeAero(double cda, double crr) {}

    record BikeEnvironment(int riderWeightKg, double windSpeed, BikeAero aero) {
        double totalMass() { return riderWeightKg + BIKE_WEIGHT_KG; }
    }

    record BikeTarget(int power, String basis, Integer computed) {}

    // ---- TARGET COMPUTATION ----

    BikeTarget computeEffectiveTarget(List<CourseSegment> segments, AthleteProfile profile, boolean isTriathlon) {
        double totalDistance = segments.stream().mapToDouble(CourseSegment::length).sum();
        double totalElevGain = segments.stream()
                .mapToDouble(s -> Math.max(0, (s.endElevation() - s.startElevation())))
                .sum();

        if (profile.targetPowerWatts() != null && profile.targetPowerWatts() > 0) {
            return new BikeTarget(profile.targetPowerWatts(), null, null);
        }

        int power = computeTarget(totalDistance, totalElevGain, profile.ftp(), isTriathlon);
        int pct = (int) Math.round(100.0 * power / profile.ftp());
        String basis = pct + "% FTP for " + PacingUtils.formatDistKm(totalDistance) + " course"
                + (totalElevGain > 50 ? " (" + (int) totalElevGain + "m climbing)" : "");
        return new BikeTarget(power, basis, power);
    }

    private int computeTarget(double totalDistM, double elevGainM, int ftp, boolean isTriathlon) {
        double[] pcts = isTriathlon ? TRI_FTP_PCTS : FTP_PCTS;
        double basePct = PacingUtils.interpolate(totalDistM, DIST_POINTS, pcts);

        double distKm = totalDistM / 1000.0;
        double gainPerKm = distKm > 0 ? elevGainM / distKm : 0;
        double elevPenalty = 0;
        if (gainPerKm > 5.0) {
            elevPenalty = ((gainPerKm - 5.0) / 10.0) * 0.015;
            elevPenalty = Math.min(elevPenalty, 0.05);
        }

        return (int) Math.round(ftp * (basePct - elevPenalty));
    }

    // ---- SEGMENT GENERATION ----

    /**
     * Generate bike pacing segments in a 4-phase pipeline:
     * 1. Allocate raw powers per segment based on gradient + aero
     * 2. Balance energy budget so time-weighted avg = GTP
     * 3. Smooth transitions (cap segment-to-segment delta at 10%)
     * 4. Re-balance after smoothing, then build output segments with fatigue/nutrition
     */
    List<PacingSegment> generateSegments(List<CourseSegment> course, AthleteProfile profile) {
        int ftp = profile.ftp();
        BikeEnvironment env = new BikeEnvironment(profile.weightKg(), 0.0, getBikeAero(profile.bikeType()));
        double gtp = profile.targetPowerWatts();

        double[] powers = allocateRawPowers(course, gtp, env);
        powers = balanceEnergyBudget(powers, course, gtp, env);
        smoothTransitions(powers, gtp);
        powers = balanceEnergyBudget(powers, course, gtp, env);
        return buildOutputSegments(powers, course, ftp, profile, env);
    }

    private double[] allocateRawPowers(List<CourseSegment> course, double gtp, BikeEnvironment env) {
        int n = course.size();
        double[] powers = new double[n];

        for (int i = 0; i < n; i++) {
            CourseSegment seg = course.get(i);
            double gradDecimal = seg.averageGradient() / 100.0;

            double gradFactor = PacingUtils.interpolate(gradDecimal, GRAD_KNOTS, GRAD_FACTORS);

            double v = speedService.steadyStateSpeed(gtp, gradDecimal, seg.startElevation(), env);
            double aeroBonus = (CDA_REFERENCE - env.aero().cda()) / CDA_REFERENCE;
            double speedFrac = Math.max(0, Math.min((v - V_LOW) / (V_HIGH - V_LOW), 1));
            double aeroCorrectionFactor = 1.0 - aeroBonus * speedFrac * AERO_BONUS_SCALE;

            powers[i] = gtp * gradFactor * aeroCorrectionFactor;
            powers[i] = clampPower(powers[i], gtp);
        }

        return powers;
    }

    private double[] balanceEnergyBudget(double[] powers, List<CourseSegment> course, double gtp, BikeEnvironment env) {
        int n = powers.length;
        double[] balanced = powers.clone();

        for (int iter = 0; iter < BUDGET_MAX_ITERATIONS; iter++) {
            double weightedPowerSum = 0;
            double totalTime = 0;

            for (int i = 0; i < n; i++) {
                CourseSegment seg = course.get(i);
                double grad = seg.averageGradient() / 100.0;
                double speed = speedService.steadyStateSpeed(balanced[i], grad, seg.startElevation(), env);
                speed = Math.max(speed, MIN_SPEED_MS);
                double time = seg.length() / speed;
                weightedPowerSum += balanced[i] * time;
                totalTime += time;
            }

            double avgPower = weightedPowerSum / totalTime;
            double scale = gtp / avgPower;

            if (Math.abs(scale - 1.0) < BUDGET_CONVERGENCE) {
                break;
            }

            for (int i = 0; i < n; i++) {
                balanced[i] *= scale;
                balanced[i] = clampPower(balanced[i], gtp);
            }
        }

        return balanced;
    }

    private void smoothTransitions(double[] powers, double gtp) {
        int n = powers.length;

        // Forward pass
        for (int i = 1; i < n; i++) {
            double lo = powers[i - 1] * (1.0 - MAX_POWER_DELTA_RATIO);
            double hi = powers[i - 1] * (1.0 + MAX_POWER_DELTA_RATIO);
            powers[i] = Math.max(lo, Math.min(powers[i], hi));
        }

        // Backward pass
        for (int i = n - 2; i >= 0; i--) {
            double lo = powers[i + 1] * (1.0 - MAX_POWER_DELTA_RATIO);
            double hi = powers[i + 1] * (1.0 + MAX_POWER_DELTA_RATIO);
            powers[i] = Math.max(lo, Math.min(powers[i], hi));
        }

        // Re-clamp after smoothing
        for (int i = 0; i < n; i++) {
            powers[i] = clampPower(powers[i], gtp);
        }
    }

    private List<PacingSegment> buildOutputSegments(double[] powers, List<CourseSegment> course, int ftp,
                                                     AthleteProfile profile, BikeEnvironment env) {
        int n = course.size();
        List<PacingSegment> result = new ArrayList<>();
        double fatigue = 0.0;
        double cumulativeTime = 0.0;
        int lastNutritionMinute = 0;
        double entrySpeed = Double.NaN;

        for (int i = 0; i < n; i++) {
            CourseSegment seg = course.get(i);
            double power = powers[i];
            double gradient = seg.averageGradient() / 100.0;

            // First segment: use its own steady-state as entry speed
            if (Double.isNaN(entrySpeed)) {
                entrySpeed = speedService.steadyStateSpeed(power, gradient, seg.startElevation(), env);
            }

            BikeSpeedService.SpeedResult speedResult = speedService.computeSegmentSpeed(
                    power, gradient, seg.startElevation(), entrySpeed, seg.length(), env);

            double speed = Math.max(speedResult.effectiveSpeed(), MIN_SPEED_MS);
            entrySpeed = speedResult.exitSpeed();

        double speedKmh = Math.round(speed * 3.6 * 10.0) / 10.0;
            double segmentTime = seg.length() / speed;

            double segmentTSS = (segmentTime / 3600.0) * Math.pow(power / ftp, 2) * 100.0;
            fatigue += segmentTSS / FATIGUE_DENOMINATOR;

            cumulativeTime += segmentTime;

            String nutrition = null;
            int currentMinute = (int) (cumulativeTime / 60.0);
            if (currentMinute - lastNutritionMinute >= NUTRITION_INTERVAL_MIN) {
                nutrition = PacingUtils.fuelSuggestion("BIKE", profile.nutritionPreference());
                lastNutritionMinute = currentMinute;
            }

            result.add(new PacingSegment(
                    seg.startDistance(), seg.endDistance(), "BIKE",
                    (int) Math.round(power), null, speedKmh,
                    Math.round(segmentTime * 10.0) / 10.0,
                    Math.round(fatigue * 1000.0) / 1000.0,
                    nutrition, seg.averageGradient(), seg.startElevation()
            ));
        }

        return result;
    }

    private double clampPower(double power, double gtp) {
        return Math.max(gtp * MIN_POWER_RATIO, Math.min(power, gtp * MAX_POWER_RATIO));
    }

    // ---- SUMMARY ----

    PacingSummary buildSummary(List<PacingSegment> segments, String targetBasis, Integer computedTarget) {
        double totalDist = segments.stream().mapToDouble(s -> s.endDistance() - s.startDistance()).sum();
        double totalTime = segments.stream().mapToDouble(PacingSegment::estimatedSegmentTime).sum();

        double weightedPowerSum = segments.stream()
                .mapToDouble(s -> s.targetPower() * s.estimatedSegmentTime()).sum();
        double avgPower = totalTime > 0 ? weightedPowerSum / totalTime : 0;

        int totalMinutes = (int) (totalTime / 60.0);
        int carbsNeeded = (int) Math.round(totalTime / 3600.0 * CARBS_PER_HOUR);
        int calories = (int) (totalTime / 3600.0 * avgPower * KCAL_FACTOR);

        return new PacingSummary(
                Math.round(totalDist),
                Math.round(totalTime * 10.0) / 10.0,
                (int) Math.round(avgPower), null,
                calories,
                carbsNeeded + "g carbs over " + totalMinutes + " min (~80g/hr)",
                targetBasis,
                computedTarget
        );
    }

    // ---- PHYSICS ----

    private static BikeAero getBikeAero(String bikeType) {
        return switch (bikeType != null ? bikeType : "ROAD_AERO") {
            case "TT"        -> new BikeAero(0.24, 0.004);
            case "ROAD_AERO" -> new BikeAero(0.28, 0.005);
            default          -> new BikeAero(0.32, 0.005);
        };
    }

}
