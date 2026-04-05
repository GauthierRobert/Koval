package com.koval.trainingplannerbackend.pacing;

import com.koval.trainingplannerbackend.pacing.dto.AthleteProfile;
import com.koval.trainingplannerbackend.pacing.dto.PacingSegment;
import com.koval.trainingplannerbackend.pacing.dto.PacingSummary;
import com.koval.trainingplannerbackend.pacing.gpx.CourseSegment;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
class RunPacingService {

    /** +4% pace per 1% gradient — aligned with Gravity Adjusted Pace (GAP) models. */
    private static final double UPHILL_FACTOR = 0.04;
    /** -2% pace per 1% downhill gradient. */
    private static final double DOWNHILL_FACTOR = 0.02;
    /** Cap downhill benefit at 6% to prevent unrealistic speeds on steep descents. */
    private static final double DOWNHILL_CAP = 0.06;

    /** Cumulative fatigue degradation per running hour. */
    private static final double FATIGUE_RATE_PER_HOUR = 0.15;
    private static final int NUTRITION_INTERVAL_MIN = 30;
    private static final double CARBS_PER_HOUR = 50.0;
    private static final double KCAL_PER_HOUR = 600.0;

    // Smart target: distance (m) → pace multiplier of threshold
    private static final double[] DIST_POINTS  = {5_000, 10_000, 21_097, 42_195};
    private static final double[] PACE_FACTORS = {0.97,  1.02,   1.08,   1.15};

    record RunTarget(int pace, String basis, Integer computed) {}

    // ---- TARGET COMPUTATION ----

    RunTarget computeEffectiveTarget(List<CourseSegment> segments, AthleteProfile profile) {
        double totalDistance = segments.stream().mapToDouble(CourseSegment::length).sum();
        double totalElevGain = segments.stream()
                .mapToDouble(s -> Math.max(0, (s.endElevation() - s.startElevation())))
                .sum();

        if (profile.targetPaceSecPerKm() != null && profile.targetPaceSecPerKm() > 0) {
            return new RunTarget(profile.targetPaceSecPerKm(), null, null);
        }

        if (profile.thresholdPaceSec() != null && profile.thresholdPaceSec() > 0) {
            int pace = computeTarget(totalDistance, totalElevGain, profile.thresholdPaceSec());
            String basis = PacingUtils.formatPace(pace, "km") + " for " + PacingUtils.formatDistKm(totalDistance)
                    + (totalElevGain > 50 ? " (" + (int) totalElevGain + "m climbing)" : "");
            return new RunTarget(pace, basis, pace);
        }

        return new RunTarget(300, null, null); // fallback 5:00/km
    }

    private int computeTarget(double totalDistM, double elevGainM, int thresholdPace) {
        double baseFactor = PacingUtils.interpolate(totalDistM, DIST_POINTS, PACE_FACTORS);

        double distKm = totalDistM / 1000.0;
        double gainPerKm = distKm > 0 ? elevGainM / distKm : 0;
        double elevPenalty = 0;
        if (gainPerKm > 5.0) {
            elevPenalty = ((gainPerKm - 5.0) / 10.0) * 0.01;
            elevPenalty = Math.min(elevPenalty, 0.05);
        }

        return (int) Math.round(thresholdPace * (baseFactor + elevPenalty));
    }

    // ---- SEGMENT GENERATION ----

    List<PacingSegment> generateSegments(List<CourseSegment> course, AthleteProfile profile, double startFatigue) {
        double targetPace = profile.targetPaceSecPerKm();

        // Phase A — Raw slope-adjusted paces (gradient only, no heat)
        double[] segPaces = new double[course.size()];

        for (int i = 0; i < course.size(); i++) {
            CourseSegment seg = course.get(i);
            double adjustedPace = targetPace;

            if (seg.averageGradient() > 0) {
                adjustedPace *= (1.0 + seg.averageGradient() * UPHILL_FACTOR);
            } else {
                double benefit = Math.abs(seg.averageGradient()) * DOWNHILL_FACTOR;
                benefit = Math.min(benefit, DOWNHILL_CAP);
                adjustedPace *= (1.0 - benefit);
            }

            segPaces[i] = adjustedPace;
        }

        // Phase B — Distance-weighted normalization (preserves heat since targetPace includes it)
        double distWeightedPaceSum = 0.0;
        double totalDistKm = 0.0;
        for (int i = 0; i < course.size(); i++) {
            double distKm = course.get(i).length() / 1000.0;
            distWeightedPaceSum += segPaces[i] * distKm;
            totalDistKm += distKm;
        }
        double normalizationFactor = targetPace / (distWeightedPaceSum / totalDistKm);
        for (int i = 0; i < course.size(); i++) {
            segPaces[i] *= normalizationFactor;
        }

        // Phase C — Build segments with normalized paces, apply fatigue degradation
        List<PacingSegment> result = new ArrayList<>();
        double fatigue = startFatigue;
        double cumulativeTime = 0.0;
        int lastNutritionMinute = 0;
        double fr = profile.fatigueResistance() != null ? profile.fatigueResistance() : 0.5;

        for (int i = 0; i < course.size(); i++) {
            CourseSegment seg = course.get(i);
            double adjustedPace = segPaces[i];

            // Apply fatigue degradation (higher pace = slower)
            adjustedPace *= (1.0 + fatigue * (1.0 - fr));

            double segmentLengthKm = seg.length() / 1000.0;
            double segmentTime = adjustedPace * segmentLengthKm;

            // Accumulate running fatigue
            fatigue += (segmentTime / 3600.0) * FATIGUE_RATE_PER_HOUR;

            cumulativeTime += segmentTime;

            // Nutrition suggestion
            String nutrition = null;
            int currentMinute = (int) (cumulativeTime / 60.0);
            if (currentMinute - lastNutritionMinute >= NUTRITION_INTERVAL_MIN) {
                nutrition = PacingUtils.fuelSuggestion("RUN", profile.nutritionPreference());
                lastNutritionMinute = currentMinute;
            }

            String paceStr = PacingUtils.formatPace((int) adjustedPace, "km");

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

    // ---- SUMMARY ----

    PacingSummary buildSummary(List<PacingSegment> segments, String targetBasis, Integer computedTarget) {
        double totalDist = segments.stream().mapToDouble(s -> s.endDistance() - s.startDistance()).sum();
        double totalTime = segments.stream().mapToDouble(PacingSegment::estimatedSegmentTime).sum();

        double totalDistKm = totalDist / 1000.0;
        double avgPaceSec = totalDistKm > 0 ? totalTime / totalDistKm : 0;

        int totalMinutes = (int) (totalTime / 60.0);
        int carbsNeeded = (int) Math.round(totalTime / 3600.0 * CARBS_PER_HOUR);
        int calories = (int) (totalTime / 3600.0 * KCAL_PER_HOUR);

        return new PacingSummary(
                Math.round(totalDist),
                Math.round(totalTime * 10.0) / 10.0,
                null,
                PacingUtils.formatPace((int) avgPaceSec, "km"),
                calories,
                carbsNeeded + "g carbs + electrolytes over " + totalMinutes + " min (~50g/hr)",
                targetBasis,
                computedTarget
        );
    }

}
