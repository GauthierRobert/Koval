package com.koval.trainingplannerbackend.pacing;

import com.koval.trainingplannerbackend.pacing.dto.AthleteProfile;
import com.koval.trainingplannerbackend.pacing.dto.PacingPlanResponse;
import com.koval.trainingplannerbackend.pacing.dto.PacingSegment;
import com.koval.trainingplannerbackend.pacing.dto.PacingSummary;
import com.koval.trainingplannerbackend.pacing.dto.RouteCoordinate;
import com.koval.trainingplannerbackend.pacing.gpx.CourseSegment;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class PacingService {

    // Cycling physics constants
    private static final double DRIVETRAIN_LOSS = 0.03;
    private static final double GRAVITY = 9.81;

    private record BikeAero(double cda, double crr) {}

    private static BikeAero getBikeAero(String bikeType) {
        return switch (bikeType != null ? bikeType : "ROAD_AERO") {
            case "TT"        -> new BikeAero(0.24, 0.004);  // full TT setup
            case "ROAD_AERO" -> new BikeAero(0.28, 0.005);  // road + clip-on extensions
            default          -> new BikeAero(0.32, 0.005);   // standard road bike
        };
    }

    // Physics-based power variability constants
    private static final double K_SHORT = 1.5;           // Sprint/Olympic (<=40km)
    private static final double K_LONG = 1.0;            // Half/Full Ironman (>40km)
    private static final double UPHILL_CAP = 1.15;       // Max: target × 1.15
    private static final double DOWNHILL_FLOOR = 0.75;   // Min: target × 0.75
    private static final double COASTING_GRADIENT = -6.0; // Below this: 0W
    private static final int NORMALIZATION_ITERATIONS = 3;

    // Run adjustment factors
    private static final double RUN_UPHILL_FACTOR = 0.04;   // +4% pace per 1% gradient
    private static final double RUN_DOWNHILL_FACTOR = 0.02;  // -2% pace per 1% gradient
    private static final double RUN_DOWNHILL_CAP = 0.06;     // Max 6% pace benefit from downhill
    private static final double HEAT_FACTOR = 0.015;         // +1.5% per degree above 20°C

    // Smart target reference points: distance (m) → % FTP
    private static final double[] BIKE_DIST_POINTS = {20_000, 40_000, 90_000, 180_000};
    private static final double[] BIKE_FTP_PCTS    = {0.95,  0.90,  0.82,   0.72};

    // Smart target reference points: distance (m) → pace multiplier of threshold
    private static final double[] RUN_DIST_POINTS  = {5_000, 10_000, 21_097, 42_195};
    private static final double[] RUN_PACE_FACTORS = {0.97,  1.02,   1.08,   1.15};

    // Smart target reference points: swim distance (m) → pace multiplier of CSS
    private static final double[] SWIM_DIST_POINTS  = {750, 1500, 1900, 3800};
    private static final double[] SWIM_PACE_FACTORS = {0.97, 1.02, 1.05, 1.08};

    public PacingPlanResponse generatePlan(List<CourseSegment> bikeSegments, List<CourseSegment> runSegments,
                                           AthleteProfile profile, String discipline,
                                           List<RouteCoordinate> bikeRouteCoordinates,
                                           List<RouteCoordinate> runRouteCoordinates) {
        // Compute course totals from the appropriate segments
        List<CourseSegment> bikeSource = bikeSegments != null ? bikeSegments : List.of();
        List<CourseSegment> runSource = runSegments != null ? runSegments : List.of();

        double bikeTotalDistance = bikeSource.stream().mapToDouble(CourseSegment::length).sum();
        double bikeTotalElevGain = bikeSource.stream()
                .mapToDouble(s -> Math.max(0, (s.endElevation() - s.startElevation())))
                .sum();
        double runTotalDistance = runSource.stream().mapToDouble(CourseSegment::length).sum();
        double runTotalElevGain = runSource.stream()
                .mapToDouble(s -> Math.max(0, (s.endElevation() - s.startElevation())))
                .sum();

        // For single-discipline, use its own totals
        double totalDistance = bikeTotalDistance > 0 ? bikeTotalDistance : runTotalDistance;
        double totalElevationGain = bikeTotalDistance > 0 ? bikeTotalElevGain : runTotalElevGain;

        // Smart default: bike target (use bike course totals)
        String bikeTargetBasis = null;
        Integer bikeComputedTarget = null;
        int effectiveBikePower;
        if (profile.targetPowerWatts() != null && profile.targetPowerWatts() > 0) {
            effectiveBikePower = profile.targetPowerWatts();
        } else {
            effectiveBikePower = computeBikeTarget(bikeTotalDistance, bikeTotalElevGain, profile.ftp());
            bikeComputedTarget = effectiveBikePower;
            int pct = (int) Math.round(100.0 * effectiveBikePower / profile.ftp());
            bikeTargetBasis = pct + "% FTP for " + formatDistKm(bikeTotalDistance) + " course"
                    + (bikeTotalElevGain > 50 ? " (" + (int) bikeTotalElevGain + "m climbing)" : "");
        }

        // Smart default: run target (use run course totals)
        String runTargetBasis = null;
        Integer runComputedTarget = null;
        int effectiveRunPace;
        if (profile.targetPaceSecPerKm() != null && profile.targetPaceSecPerKm() > 0) {
            effectiveRunPace = profile.targetPaceSecPerKm();
        } else if (profile.thresholdPaceSec() != null && profile.thresholdPaceSec() > 0) {
            effectiveRunPace = computeRunTarget(runTotalDistance, runTotalElevGain, profile.thresholdPaceSec());
            runComputedTarget = effectiveRunPace;
            int paceMin = effectiveRunPace / 60;
            int paceSec = effectiveRunPace % 60;
            runTargetBasis = paceMin + ":" + String.format("%02d", paceSec) + "/km for "
                    + formatDistKm(runTotalDistance)
                    + (runTotalElevGain > 50 ? " (" + (int) runTotalElevGain + "m climbing)" : "");
        } else {
            effectiveRunPace = 300; // fallback 5:00/km
        }

        // Build effective profile with computed targets
        AthleteProfile effectiveProfile = new AthleteProfile(
                profile.ftp(), profile.weightKg(), profile.thresholdPaceSec(), profile.swimCssSec(),
                profile.fatigueResistance(), profile.nutritionPreference(),
                profile.temperature(), profile.windSpeed(),
                effectiveBikePower, effectiveRunPace,
                profile.swimDistanceM(), profile.targetSwimPaceSecPer100m(),
                profile.bikeType()
        );

        List<PacingSegment> bikePacingSegments = null;
        List<PacingSegment> runPacingSegments = null;
        PacingSummary bikeSummary = null;
        PacingSummary runSummary = null;

        double priorFatigue = 0.0;

        if ("BIKE".equals(discipline) || "TRIATHLON".equals(discipline)) {
            bikePacingSegments = generateBikeSegments(bikeSource, effectiveProfile, 0.0);
            bikeSummary = buildBikeSummary(bikePacingSegments, effectiveProfile, bikeTargetBasis, bikeComputedTarget);
            priorFatigue = bikePacingSegments.isEmpty() ? 0.0
                    : bikePacingSegments.getLast().cumulativeFatigue();
        }

        if ("RUN".equals(discipline) || "TRIATHLON".equals(discipline)) {
            runPacingSegments = generateRunSegments(runSource, effectiveProfile, priorFatigue);
            runSummary = buildRunSummary(runPacingSegments, effectiveProfile, runTargetBasis, runComputedTarget);
        }

        // Swim summary (no segments — just time/pace estimate)
        PacingSummary swimSummary = null;
        if ("SWIM".equals(discipline) || "TRIATHLON".equals(discipline)) {
            swimSummary = buildSwimSummary(profile);
        }

        return new PacingPlanResponse(bikePacingSegments, runPacingSegments, bikeSummary, runSummary, swimSummary,
                bikeRouteCoordinates, runRouteCoordinates);
    }

    // ---- SMART TARGET COMPUTATION ----

    private static double interpolate(double x, double[] xs, double[] ys) {
        if (x <= xs[0]) return ys[0];
        if (x >= xs[xs.length - 1]) return ys[ys.length - 1];
        for (int i = 0; i < xs.length - 1; i++) {
            if (x <= xs[i + 1]) {
                double ratio = (x - xs[i]) / (xs[i + 1] - xs[i]);
                return ys[i] + ratio * (ys[i + 1] - ys[i]);
            }
        }
        return ys[ys.length - 1];
    }

    private int computeBikeTarget(double totalDistM, double elevGainM, int ftp) {
        double basePct = interpolate(totalDistM, BIKE_DIST_POINTS, BIKE_FTP_PCTS);

        // Elevation penalty: -1.5% FTP per 10 m/km above 5 m/km baseline, capped at 5%
        double distKm = totalDistM / 1000.0;
        double gainPerKm = distKm > 0 ? elevGainM / distKm : 0;
        double elevPenalty = 0;
        if (gainPerKm > 5.0) {
            elevPenalty = ((gainPerKm - 5.0) / 10.0) * 0.015;
            elevPenalty = Math.min(elevPenalty, 0.05);
        }

        return (int) Math.round(ftp * (basePct - elevPenalty));
    }

    private int computeRunTarget(double totalDistM, double elevGainM, int thresholdPace) {
        double baseFactor = interpolate(totalDistM, RUN_DIST_POINTS, RUN_PACE_FACTORS);

        // Elevation penalty: +1% pace per 10 m/km above 5 m/km baseline, capped at 5%
        double distKm = totalDistM / 1000.0;
        double gainPerKm = distKm > 0 ? elevGainM / distKm : 0;
        double elevPenalty = 0;
        if (gainPerKm > 5.0) {
            elevPenalty = ((gainPerKm - 5.0) / 10.0) * 0.01;
            elevPenalty = Math.min(elevPenalty, 0.05);
        }

        return (int) Math.round(thresholdPace * (baseFactor + elevPenalty));
    }

    private int computeSwimTarget(double swimDistM, int cssPer100m) {
        double factor = interpolate(swimDistM, SWIM_DIST_POINTS, SWIM_PACE_FACTORS);
        return (int) Math.round(cssPer100m * factor);
    }

    // ---- BIKE PACING ----

    private double computeSegmentPower(double gradientPercent, double targetAvgPower, double k) {
        if (gradientPercent < COASTING_GRADIENT) return 0.0;
        double gradientDecimal = gradientPercent / 100.0;
        double power = targetAvgPower * (1.0 + gradientDecimal * k);
        return Math.max(targetAvgPower * DOWNHILL_FLOOR, Math.min(power, targetAvgPower * UPHILL_CAP));
    }

    private double getVariabilityK(double totalDistanceM) {
        return totalDistanceM <= 40_000 ? K_SHORT : K_LONG;
    }

    private List<PacingSegment> generateBikeSegments(List<CourseSegment> course, AthleteProfile profile, double startFatigue) {
        int ftp = profile.ftp();
        int weight = profile.weightKg();
        BikeAero aero = getBikeAero(profile.bikeType());

        double targetPower = profile.targetPowerWatts();
        double totalDistanceM = course.stream().mapToDouble(CourseSegment::length).sum();
        double k = getVariabilityK(totalDistanceM);

        // Phase A — Compute initial powers and identify coasting segments
        double[] segPowers = new double[course.size()];
        boolean[] isCoasting = new boolean[course.size()];
        for (int i = 0; i < course.size(); i++) {
            double gradient = course.get(i).averageGradient();
            isCoasting[i] = gradient < COASTING_GRADIENT;
            segPowers[i] = computeSegmentPower(gradient, targetPower, k);
        }

        // Phase B — Iterative normalization (scale only non-coasting segments)
        for (int iter = 0; iter < NORMALIZATION_ITERATIONS; iter++) {
            double totalTime = 0.0;
            double nonCoastingTime = 0.0;
            double nonCoastingPowerTimeSum = 0.0;

            for (int i = 0; i < course.size(); i++) {
                CourseSegment seg = course.get(i);
                double gradient = seg.averageGradient() / 100.0;
                double speed = estimateSpeedFromPower(segPowers[i], weight, gradient, profile.windSpeed(), seg.startElevation(), aero.cda, aero.crr);
                speed = Math.max(speed, 2.0);
                double segTime = seg.length() / speed;

                totalTime += segTime;
                if (!isCoasting[i]) {
                    nonCoastingTime += segTime;
                    nonCoastingPowerTimeSum += segPowers[i] * segTime;
                }
            }

            if (nonCoastingTime > 0) {
                double desiredNonCoastingAvg = targetPower * totalTime / nonCoastingTime;
                double currentNonCoastingAvg = nonCoastingPowerTimeSum / nonCoastingTime;
                double scale = desiredNonCoastingAvg / currentNonCoastingAvg;
                for (int i = 0; i < course.size(); i++) {
                    if (!isCoasting[i]) {
                        segPowers[i] *= scale;
                        // Re-apply safety clamps
                        segPowers[i] = Math.max(targetPower * DOWNHILL_FLOOR, Math.min(segPowers[i], targetPower * UPHILL_CAP));
                    }
                }
            }
        }

        // Phase C — Build segments with converged powers
        List<PacingSegment> result = new ArrayList<>();
        double fatigue = startFatigue;
        double cumulativeTime = 0.0;
        int lastNutritionMinute = 0;

        for (int i = 0; i < course.size(); i++) {
            CourseSegment seg = course.get(i);
            double power = segPowers[i];
            double gradient = seg.averageGradient() / 100.0;

            double speed = estimateSpeedFromPower(power, weight, gradient, profile.windSpeed(), seg.startElevation(), aero.cda, aero.crr);
            speed = Math.max(speed, 2.0);
            double speedKmh = Math.round(speed * 3.6 * 10.0) / 10.0;

            double segmentTime = seg.length() / speed;

            // Accumulate fatigue (TSS-based)
            // TODO: Scale fatigue rate by profile.fatigueResistance()
            double segmentTSS = (segmentTime / 3600.0) * Math.pow(power / ftp, 2) * 100.0;
            fatigue += segmentTSS / 500.0;

            cumulativeTime += segmentTime;

            // Nutrition suggestion every ~20 minutes
            String nutrition = null;
            int currentMinute = (int) (cumulativeTime / 60.0);
            if (currentMinute - lastNutritionMinute >= 20) {
                nutrition = bikeFuelSuggestion(profile.nutritionPreference());
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

    /**
     * Estimate cycling speed (m/s) from power using simplified physics.
     * P = (Crr * m * g * v) + (0.5 * CdA * rho * (v+w)^2 * v) + (m * g * grade * v)
     * Solved iteratively via Newton's method.
     * Air density adjusted for altitude: rho = 1.225 * exp(-elevation / 8500).
     */
    private double estimateSpeedFromPower(double power, int weightKg, double gradient, double windSpeed, double elevation, double cda, double crr) {
        double effectivePower = power * (1.0 - DRIVETRAIN_LOSS);
        double mass = weightKg + 8.0; // rider + bike
        double rho = 1.225 * Math.exp(-elevation / 8500.0); // altitude-adjusted air density

        // Newton's method to solve for v
        double v = 8.0; // initial guess (m/s)
        for (int i = 0; i < 20; i++) {
            double airSpeed = v + windSpeed;
            double resistancePower = crr * mass * GRAVITY * v
                    + 0.5 * cda * rho * airSpeed * airSpeed * v
                    + mass * GRAVITY * gradient * v;
            // Correct derivative: d/dv[(v+w)^2*v] = (v+w)^2 + 2*v*(v+w)
            double derivative = crr * mass * GRAVITY
                    + 0.5 * cda * rho * (airSpeed * airSpeed + 2.0 * v * airSpeed)
                    + mass * GRAVITY * gradient;

            double error = resistancePower - effectivePower;
            v -= error / derivative;
            v = Math.max(v, 1.0);
        }

        return Math.min(v, 22.0); // Cap at ~80 km/h for descents (braking)
    }

    // ---- RUN PACING ----

    private List<PacingSegment> generateRunSegments(List<CourseSegment> course, AthleteProfile profile, double startFatigue) {
        // Target pace already computed (smart default or user override) in effectiveProfile
        double targetPace = profile.targetPaceSecPerKm();

        // Phase B — Raw slope-adjusted paces
        double[] segPaces = new double[course.size()];
        double temp = profile.temperature() != null ? profile.temperature() : 20.0;

        for (int i = 0; i < course.size(); i++) {
            CourseSegment seg = course.get(i);
            double adjustedPace = targetPace;

            // Gradient adjustment
            if (seg.averageGradient() > 0) {
                adjustedPace *= (1.0 + seg.averageGradient() * RUN_UPHILL_FACTOR);
            } else {
                double benefit = Math.abs(seg.averageGradient()) * RUN_DOWNHILL_FACTOR;
                benefit = Math.min(benefit, RUN_DOWNHILL_CAP);
                adjustedPace *= (1.0 - benefit);
            }

            // Heat adjustment
            if (temp > 20.0) {
                adjustedPace *= (1.0 + (temp - 20.0) * HEAT_FACTOR);
            }

            segPaces[i] = adjustedPace;
        }

        // Phase C — Distance-weighted normalization
        double weightedSum = 0.0;
        double totalDist = 0.0;
        for (int i = 0; i < course.size(); i++) {
            double distKm = course.get(i).length() / 1000.0;
            weightedSum += segPaces[i] * distKm;
            totalDist += distKm;
        }
        double scale = targetPace / (weightedSum / totalDist);
        for (int i = 0; i < course.size(); i++) {
            segPaces[i] *= scale;
        }

        // Phase D — Build segments with normalized paces
        List<PacingSegment> result = new ArrayList<>();
        double fatigue = startFatigue;
        double cumulativeTime = 0.0;
        int lastNutritionMinute = 0;

        for (int i = 0; i < course.size(); i++) {
            CourseSegment seg = course.get(i);
            double adjustedPace = segPaces[i];

            double segmentLengthKm = seg.length() / 1000.0;
            double segmentTime = adjustedPace * segmentLengthKm;

            // Accumulate running fatigue
            // TODO: Scale fatigue rate by profile.fatigueResistance()
            fatigue += (segmentTime / 3600.0) * 0.15;

            cumulativeTime += segmentTime;

            // Nutrition every ~30 minutes
            String nutrition = null;
            int currentMinute = (int) (cumulativeTime / 60.0);
            if (currentMinute - lastNutritionMinute >= 30) {
                nutrition = runFuelSuggestion(profile.nutritionPreference());
                lastNutritionMinute = currentMinute;
            }

            // Format pace as M:SS
            int paceMin = (int) (adjustedPace / 60.0);
            int paceSec = (int) (adjustedPace % 60.0);
            String paceStr = paceMin + ":" + String.format("%02d", paceSec) + " /km";

            result.add(new PacingSegment(
                    seg.startDistance(), seg.endDistance(), "RUN",
                    null, paceStr, null,
                    Math.round(segmentTime * 10.0) / 10.0,
                    Math.round(fatigue * 1000.0) / 1000.0,
                    nutrition, seg.averageGradient(), seg.startElevation()
            ));
        }

        return result;
    }

    // ---- SUMMARIES ----

    private PacingSummary buildBikeSummary(List<PacingSegment> segments, AthleteProfile profile,
                                           String targetBasis, Integer computedTarget) {
        double totalDist = segments.stream().mapToDouble(s -> s.endDistance() - s.startDistance()).sum();
        double totalTime = segments.stream().mapToDouble(PacingSegment::estimatedSegmentTime).sum();

        // Time-weighted average power
        double weightedPowerSum = 0.0;
        for (PacingSegment s : segments) {
            weightedPowerSum += s.targetPower() * s.estimatedSegmentTime();
        }
        double avgPower = totalTime > 0 ? weightedPowerSum / totalTime : 0;

        int totalMinutes = (int) (totalTime / 60.0);
        int carbsNeeded = (int) Math.round(totalTime / 3600.0 * 80); // ~80g/hr
        int calories = (int) (totalTime / 3600.0 * avgPower * 3.6); // rough kcal estimate

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

    private PacingSummary buildRunSummary(List<PacingSegment> segments, AthleteProfile profile,
                                          String targetBasis, Integer computedTarget) {
        double totalDist = segments.stream().mapToDouble(s -> s.endDistance() - s.startDistance()).sum();
        double totalTime = segments.stream().mapToDouble(PacingSegment::estimatedSegmentTime).sum();

        // Distance-weighted average pace: totalTime / (totalDist in km)
        double totalDistKm = totalDist / 1000.0;
        double avgPaceSec = totalDistKm > 0 ? totalTime / totalDistKm : 0;
        int avgMin = (int) (avgPaceSec / 60);
        int avgSec = (int) (avgPaceSec % 60);

        int totalMinutes = (int) (totalTime / 60.0);
        int carbsNeeded = (int) Math.round(totalTime / 3600.0 * 50); // ~50g/hr
        int calories = (int) (totalTime / 3600.0 * 600); // ~600 kcal/hr running

        return new PacingSummary(
                Math.round(totalDist),
                Math.round(totalTime * 10.0) / 10.0,
                null,
                avgMin + ":" + String.format("%02d", avgSec) + " /km",
                calories,
                carbsNeeded + "g carbs + electrolytes over " + totalMinutes + " min (~50g/hr)",
                targetBasis,
                computedTarget
        );
    }

    private PacingSummary buildSwimSummary(AthleteProfile profile) {
        if (profile.swimDistanceM() == null || profile.swimDistanceM() <= 0
                || profile.swimCssSec() == null || profile.swimCssSec() <= 0) {
            return null;
        }

        int swimDist = profile.swimDistanceM();
        int cssPer100m = profile.swimCssSec();

        int targetPace;
        String targetBasis;
        Integer computedTarget;

        if (profile.targetSwimPaceSecPer100m() != null && profile.targetSwimPaceSecPer100m() > 0) {
            targetPace = profile.targetSwimPaceSecPer100m();
            targetBasis = null;
            computedTarget = null;
        } else {
            targetPace = computeSwimTarget(swimDist, cssPer100m);
            computedTarget = targetPace;
            int paceMin = targetPace / 60;
            int paceSec = targetPace % 60;
            targetBasis = paceMin + ":" + String.format("%02d", paceSec) + "/100m for " + swimDist + "m swim";
        }

        double estimatedTime = (swimDist / 100.0) * targetPace;
        int paceMin = targetPace / 60;
        int paceSec = targetPace % 60;

        return new PacingSummary(
                swimDist,
                Math.round(estimatedTime * 10.0) / 10.0,
                null,
                paceMin + ":" + String.format("%02d", paceSec) + " /100m",
                0,
                "Hydrate well before start",
                targetBasis,
                computedTarget
        );
    }

    // ---- NUTRITION HELPERS ----

    private String bikeFuelSuggestion(String preference) {
        return switch (preference) {
            case "GELS" -> "Take 1 energy gel (25g carbs) + 200ml water";
            case "DRINK" -> "Drink 300ml carb drink (25-30g carbs)";
            case "SOLID" -> "Eat half energy bar (20-25g carbs) + 200ml water";
            default -> "25-30g carbs: gel, bar piece, or drink mix + water";
        };
    }

    private String runFuelSuggestion(String preference) {
        return switch (preference) {
            case "GELS" -> "Take 1 gel (25g carbs) + water at aid station";
            case "DRINK" -> "200ml sports drink (20-25g carbs) + water";
            case "SOLID" -> "2-3 energy chews (15-20g carbs) + water";
            default -> "20-25g carbs: gel or sports drink + water";
        };
    }

    // ---- HELPERS ----

    private String formatDistKm(double meters) {
        double km = meters / 1000.0;
        if (km >= 10) {
            return (int) Math.round(km) + "km";
        }
        return String.format("%.1fkm", km);
    }
}
