package com.koval.trainingplannerbackend.pacing;

import com.koval.trainingplannerbackend.pacing.dto.*;
import com.koval.trainingplannerbackend.pacing.gpx.CourseSegment;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class PacingService {

    // Cycling physics constants
    private static final double CRR = 0.005;        // Rolling resistance coefficient
    private static final double CDA = 0.32;          // Drag area (m^2) for aero position
    private static final double AIR_DENSITY = 1.225; // kg/m^3 at sea level, 15°C
    private static final double DRIVETRAIN_LOSS = 0.03;
    private static final double GRAVITY = 9.81;

    // Pacing intensity factors (fraction of FTP)
    private static final double BIKE_INTENSITY_FACTOR = 0.75;
    private static final double MIN_POWER_FRACTION = 0.55;

    // Run adjustment factors
    private static final double RUN_UPHILL_FACTOR = 0.04;   // +4% pace per 1% gradient
    private static final double RUN_DOWNHILL_FACTOR = 0.02;  // -2% pace per 1% gradient
    private static final double RUN_DOWNHILL_CAP = 0.06;     // Max 6% pace benefit from downhill
    private static final double HEAT_FACTOR = 0.015;         // +1.5% per degree above 20°C

    public PacingPlanResponse generatePlan(List<CourseSegment> segments, AthleteProfile profile, String discipline) {
        List<PacingSegment> bikeSegments = null;
        List<PacingSegment> runSegments = null;
        PacingSummary bikeSummary = null;
        PacingSummary runSummary = null;

        double priorFatigue = 0.0;

        if ("BIKE".equals(discipline) || "BOTH".equals(discipline)) {
            bikeSegments = generateBikeSegments(segments, profile, 0.0);
            bikeSummary = buildBikeSummary(bikeSegments, profile);
            priorFatigue = bikeSegments.isEmpty() ? 0.0
                    : bikeSegments.get(bikeSegments.size() - 1).cumulativeFatigue();
        }

        if ("RUN".equals(discipline) || "BOTH".equals(discipline)) {
            runSegments = generateRunSegments(segments, profile, priorFatigue);
            runSummary = buildRunSummary(runSegments, profile);
        }

        return new PacingPlanResponse(bikeSegments, runSegments, bikeSummary, runSummary);
    }

    // ---- BIKE PACING ----

    private List<PacingSegment> generateBikeSegments(List<CourseSegment> course, AthleteProfile profile, double startFatigue) {
        List<PacingSegment> result = new ArrayList<>();
        int ftp = profile.ftp();
        int weight = profile.weightKg();
        double fatigue = startFatigue;
        double cumulativeTime = 0.0;
        int lastNutritionMinute = 0;

        for (CourseSegment seg : course) {
            double gradient = seg.averageGradient() / 100.0; // convert % to fraction
            double basePower = ftp * BIKE_INTENSITY_FACTOR;

            // Gradient adjustment: increase power on climbs, reduce on descents
            double gradientAdjustment = 1.0 + (seg.averageGradient() * 0.025);
            double targetPower = basePower * gradientAdjustment;

            // Apply fatigue reduction
            double fatigueReduction = fatigue * (1.0 - profile.fatigueResistance());
            targetPower *= (1.0 - fatigueReduction);

            // Clamp power
            targetPower = Math.max(targetPower, ftp * MIN_POWER_FRACTION);
            targetPower = Math.min(targetPower, ftp * 1.2);

            // Estimate speed from power using cycling physics
            double speed = estimateSpeedFromPower(targetPower, weight, gradient, profile.windSpeed());
            speed = Math.max(speed, 2.0); // Minimum 2 m/s (~7 km/h)

            double segmentLength = seg.length();
            double segmentTime = segmentLength / speed;

            // Accumulate fatigue (simplified TSS-based model)
            double segmentTSS = (segmentTime / 3600.0) * Math.pow(targetPower / ftp, 2) * 100.0;
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
                    (int) Math.round(targetPower), null,
                    Math.round(segmentTime * 10.0) / 10.0,
                    Math.round(fatigue * 1000.0) / 1000.0,
                    nutrition, seg.averageGradient(), seg.startElevation()
            ));
        }

        return result;
    }

    /**
     * Estimate cycling speed (m/s) from power using simplified physics.
     * P = (Crr * m * g * v) + (0.5 * CdA * rho * v^3) + (m * g * grade * v)
     * Solved iteratively via Newton's method.
     */
    private double estimateSpeedFromPower(double power, int weightKg, double gradient, double windSpeed) {
        double effectivePower = power * (1.0 - DRIVETRAIN_LOSS);
        double mass = weightKg + 8.0; // rider + bike

        // Newton's method to solve for v
        double v = 8.0; // initial guess (m/s)
        for (int i = 0; i < 20; i++) {
            double airSpeed = v + (windSpeed != null ? windSpeed : 0.0);
            double resistancePower = CRR * mass * GRAVITY * v
                    + 0.5 * CDA * AIR_DENSITY * airSpeed * airSpeed * v
                    + mass * GRAVITY * gradient * v;
            double derivative = CRR * mass * GRAVITY
                    + 0.5 * CDA * AIR_DENSITY * (3.0 * airSpeed * airSpeed)
                    + mass * GRAVITY * gradient;

            double error = resistancePower - effectivePower;
            v -= error / derivative;
            v = Math.max(v, 1.0);
        }

        return v;
    }

    // ---- RUN PACING ----

    private List<PacingSegment> generateRunSegments(List<CourseSegment> course, AthleteProfile profile, double startFatigue) {
        List<PacingSegment> result = new ArrayList<>();
        int thresholdPace = profile.thresholdPaceSec(); // sec per km
        double fatigue = startFatigue;
        double cumulativeTime = 0.0;
        int lastNutritionMinute = 0;

        // Race pace is typically slightly slower than threshold
        double basePaceSecPerKm = thresholdPace * 1.05;

        for (CourseSegment seg : course) {
            double adjustedPace = basePaceSecPerKm;

            // Gradient adjustment
            if (seg.averageGradient() > 0) {
                adjustedPace *= (1.0 + seg.averageGradient() * RUN_UPHILL_FACTOR);
            } else {
                double benefit = Math.abs(seg.averageGradient()) * RUN_DOWNHILL_FACTOR;
                benefit = Math.min(benefit, RUN_DOWNHILL_CAP);
                adjustedPace *= (1.0 - benefit);
            }

            // Heat adjustment
            double temp = profile.temperature() != null ? profile.temperature() : 20.0;
            if (temp > 20.0) {
                adjustedPace *= (1.0 + (temp - 20.0) * HEAT_FACTOR);
            }

            // Fatigue adjustment
            double fatigueReduction = fatigue * (1.0 - profile.fatigueResistance());
            adjustedPace *= (1.0 + fatigueReduction * 0.5);

            double segmentLengthKm = seg.length() / 1000.0;
            double segmentTime = adjustedPace * segmentLengthKm;

            // Accumulate running fatigue
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
                    null, paceStr,
                    Math.round(segmentTime * 10.0) / 10.0,
                    Math.round(fatigue * 1000.0) / 1000.0,
                    nutrition, seg.averageGradient(), seg.startElevation()
            ));
        }

        return result;
    }

    // ---- SUMMARIES ----

    private PacingSummary buildBikeSummary(List<PacingSegment> segments, AthleteProfile profile) {
        double totalDist = segments.stream().mapToDouble(s -> s.endDistance() - s.startDistance()).sum();
        double totalTime = segments.stream().mapToDouble(PacingSegment::estimatedSegmentTime).sum();
        double avgPower = segments.stream().mapToInt(PacingSegment::targetPower).average().orElse(0);

        int totalMinutes = (int) (totalTime / 60.0);
        int carbsNeeded = (totalMinutes / 20) * 75; // ~75g per 20 min
        int calories = (int) (totalTime / 3600.0 * avgPower * 3.6); // rough kcal estimate

        return new PacingSummary(
                Math.round(totalDist) / 1.0,
                Math.round(totalTime * 10.0) / 10.0,
                (int) Math.round(avgPower), null,
                calories,
                carbsNeeded + "g carbs over " + totalMinutes + " min"
        );
    }

    private PacingSummary buildRunSummary(List<PacingSegment> segments, AthleteProfile profile) {
        double totalDist = segments.stream().mapToDouble(s -> s.endDistance() - s.startDistance()).sum();
        double totalTime = segments.stream().mapToDouble(PacingSegment::estimatedSegmentTime).sum();

        // Parse average pace from segment paces
        double totalPaceSec = 0;
        for (PacingSegment s : segments) {
            String paceStr = s.targetPace().replace(" /km", "");
            String[] parts = paceStr.split(":");
            totalPaceSec += Integer.parseInt(parts[0]) * 60 + Integer.parseInt(parts[1]);
        }
        double avgPaceSec = totalPaceSec / segments.size();
        int avgMin = (int) (avgPaceSec / 60);
        int avgSec = (int) (avgPaceSec % 60);

        int totalMinutes = (int) (totalTime / 60.0);
        int carbsNeeded = (totalMinutes / 30) * 45;
        int calories = (int) (totalTime / 3600.0 * 600); // ~600 kcal/hr running

        return new PacingSummary(
                Math.round(totalDist) / 1.0,
                Math.round(totalTime * 10.0) / 10.0,
                null,
                avgMin + ":" + String.format("%02d", avgSec) + " /km",
                calories,
                carbsNeeded + "g carbs + electrolytes over " + totalMinutes + " min"
        );
    }

    // ---- NUTRITION HELPERS ----

    private String bikeFuelSuggestion(String preference) {
        return switch (preference) {
            case "GELS" -> "Take 1 energy gel (25g carbs) + 500ml water";
            case "DRINK" -> "Drink 500ml carb drink (60g carbs)";
            case "SOLID" -> "Eat 1 energy bar (40g carbs) + 250ml water";
            default -> "60-90g carbs: gel, bar, or drink mix + water";
        };
    }

    private String runFuelSuggestion(String preference) {
        return switch (preference) {
            case "GELS" -> "Take 1 gel (25g carbs) + water at aid station";
            case "DRINK" -> "250ml sports drink (30g carbs) + electrolytes";
            case "SOLID" -> "Small piece of banana or energy chew + water";
            default -> "30-60g carbs: gel or sports drink + electrolytes";
        };
    }
}
