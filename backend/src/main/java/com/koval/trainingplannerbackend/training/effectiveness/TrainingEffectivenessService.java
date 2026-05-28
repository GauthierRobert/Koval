package com.koval.trainingplannerbackend.training.effectiveness;

import com.koval.trainingplannerbackend.training.history.CompletedSession;
import com.koval.trainingplannerbackend.training.history.CompletedSessionRepository;
import com.koval.trainingplannerbackend.training.metrics.PowerCurveService;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Computes per-athlete training effectiveness across a window by attributing power-curve gains
 * to workout families in proportion to their TSS share.
 *
 * <p>Algorithm:
 * <ol>
 *   <li>Pull completed sessions in [from, to].</li>
 *   <li>Split the window in half at the midpoint date; compute best power curves for both halves.</li>
 *   <li>Per duration (5min/20min/60min), gain = secondHalf − firstHalf.</li>
 *   <li>Classify each session into a {@link WorkoutFamily} via {@link WorkoutFamilyClassifier}.</li>
 *   <li>For each family aggregate count, total TSS, total duration, avg IF/alignment/RPE.</li>
 *   <li>Attribute the 20-minute gain to each family by its TSS share, normalize to watts per
 *       1000 TSS, and rank families by that value.</li>
 * </ol>
 *
 * <p>This is a transparent dose-response heuristic, not a causal model.
 */
@Service
public class TrainingEffectivenessService {

    /** Threshold (20-minute) duration in seconds — anchor for the ranking metric. */
    private static final int THRESHOLD_DURATION_SECONDS = 1200;

    /** Durations (seconds) for which we expose first-half / second-half curves. */
    private static final int[] REPORTED_DURATIONS = {300, 1200, 3600};

    private final CompletedSessionRepository sessionRepository;
    private final PowerCurveService powerCurveService;
    private final WorkoutFamilyClassifier classifier;

    public TrainingEffectivenessService(CompletedSessionRepository sessionRepository,
                                        PowerCurveService powerCurveService,
                                        WorkoutFamilyClassifier classifier) {
        this.sessionRepository = sessionRepository;
        this.powerCurveService = powerCurveService;
        this.classifier = classifier;
    }

    public TrainingEffectivenessReport evaluate(String athleteId, LocalDate from, LocalDate to) {
        if (from == null || to == null || !from.isBefore(to)) {
            throw new IllegalArgumentException("from must be before to (YYYY-MM-DD).");
        }

        List<CompletedSession> sessions = sessionRepository.findByUserIdAndCompletedAtBetween(
                athleteId, LocalDateTime.of(from, LocalTime.MIN), LocalDateTime.of(to, LocalTime.MAX));

        LocalDate split = from.plusDays((to.toEpochDay() - from.toEpochDay()) / 2);

        Map<Integer, Double> firstHalfCurve = filteredCurve(
                powerCurveService.getBestPowerCurve(athleteId, from, split));
        Map<Integer, Double> secondHalfCurve = filteredCurve(
                powerCurveService.getBestPowerCurve(athleteId, split.plusDays(1), to));

        Map<Integer, Double> gains = new HashMap<>();
        for (int d : REPORTED_DURATIONS) {
            double before = firstHalfCurve.getOrDefault(d, 0.0);
            double after = secondHalfCurve.getOrDefault(d, 0.0);
            gains.put(d, after - before);
        }

        Map<WorkoutFamily, FamilyAgg> aggs = new EnumMap<>(WorkoutFamily.class);
        double totalTss = 0.0;
        for (CompletedSession s : sessions) {
            WorkoutFamily fam = classifier.classify(s);
            FamilyAgg agg = aggs.computeIfAbsent(fam, k -> new FamilyAgg());
            agg.count++;
            agg.durationSec += s.getTotalDurationSeconds();
            if (s.getTss() != null) {
                agg.totalTss += s.getTss();
                totalTss += s.getTss();
            }
            if (s.getIntensityFactor() != null) {
                agg.ifSum += s.getIntensityFactor();
                agg.ifCount++;
            }
            if (s.getRpe() != null) {
                agg.rpeSum += s.getRpe();
                agg.rpeCount++;
            }
            Integer align = s.getAlignmentScore() != null
                    ? s.getAlignmentScore().effectiveScore() : null;
            if (align != null) {
                agg.alignSum += align;
                agg.alignCount++;
            }
        }

        double thresholdGain = gains.getOrDefault(THRESHOLD_DURATION_SECONDS, 0.0);

        List<FamilyEffectiveness> families = new ArrayList<>();
        for (Map.Entry<WorkoutFamily, FamilyAgg> e : aggs.entrySet()) {
            FamilyAgg a = e.getValue();
            double share = totalTss > 0 ? a.totalTss / totalTss : 0.0;
            Double wattsPer1kTss = null;
            if (a.totalTss > 0) {
                wattsPer1kTss = (thresholdGain * share) * 1000.0 / a.totalTss;
            }
            families.add(new FamilyEffectiveness(
                    e.getKey(),
                    a.count,
                    round(a.totalTss, 1),
                    round(share, 3),
                    a.durationSec,
                    a.ifCount > 0 ? round(a.ifSum / a.ifCount, 3) : null,
                    a.alignCount > 0 ? round(a.alignSum / (double) a.alignCount, 1) : null,
                    a.rpeCount > 0 ? round(a.rpeSum / (double) a.rpeCount, 1) : null,
                    wattsPer1kTss != null ? round(wattsPer1kTss, 2) : null,
                    0));
        }
        families.sort(Comparator.comparing(
                (FamilyEffectiveness f) -> f.estimatedWattsPer1000Tss() == null
                        ? Double.NEGATIVE_INFINITY : f.estimatedWattsPer1000Tss())
                .reversed());
        for (int i = 0; i < families.size(); i++) {
            FamilyEffectiveness f = families.get(i);
            families.set(i, new FamilyEffectiveness(
                    f.family(), f.sessionCount(), f.totalTss(), f.tssShare(),
                    f.totalDurationSeconds(), f.avgIntensityFactor(), f.avgAlignment(),
                    f.avgRpe(), f.estimatedWattsPer1000Tss(), i + 1));
        }

        String summary = buildSummary(sessions.size(), thresholdGain, families);

        return new TrainingEffectivenessReport(
                athleteId, from, to, split, sessions.size(), round(totalTss, 1),
                firstHalfCurve, secondHalfCurve, gains, families, summary);
    }

    private Map<Integer, Double> filteredCurve(Map<Integer, Double> raw) {
        Map<Integer, Double> out = new HashMap<>();
        if (raw == null) return out;
        for (int d : REPORTED_DURATIONS) {
            Double v = raw.get(d);
            if (v != null) out.put(d, v);
        }
        return out;
    }

    private String buildSummary(int sessions, double thresholdGain, List<FamilyEffectiveness> families) {
        if (sessions == 0) return "No completed sessions in the window — no effectiveness signal.";
        if (families.isEmpty()) return "Sessions found but missing TSS — cannot estimate effectiveness.";

        FamilyEffectiveness top = families.get(0);
        String direction = thresholdGain > 5
                ? String.format(Locale.ROOT, "20-min power rose %.0f W over the window", thresholdGain)
                : thresholdGain < -5
                        ? String.format(Locale.ROOT, "20-min power fell %.0f W over the window", -thresholdGain)
                        : "20-min power was flat over the window";

        if (top.estimatedWattsPer1000Tss() == null || top.estimatedWattsPer1000Tss() <= 0) {
            return direction + ". With this sample size no family showed a positive return on TSS — "
                    + "extend the window or recheck FIT-file ingest.";
        }
        return String.format(Locale.ROOT,
                "%s. Highest return came from %s (%.1f W per 1000 TSS across %d session%s).",
                direction, top.family(), top.estimatedWattsPer1000Tss(),
                top.sessionCount(), top.sessionCount() == 1 ? "" : "s");
    }

    private static double round(double value, int decimals) {
        double scale = Math.pow(10, decimals);
        return Math.round(value * scale) / scale;
    }

    private static final class FamilyAgg {
        int count;
        int durationSec;
        double totalTss;
        double ifSum;
        int ifCount;
        long alignSum;
        int alignCount;
        double rpeSum;
        int rpeCount;
    }
}
