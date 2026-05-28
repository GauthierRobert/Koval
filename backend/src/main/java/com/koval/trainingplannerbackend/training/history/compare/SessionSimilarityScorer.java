package com.koval.trainingplannerbackend.training.history.compare;

import com.koval.trainingplannerbackend.training.history.CompletedSession;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Pure scoring function: produces a 0–100 similarity percent between two
 * {@link CompletedSession}s of the same sport. Higher = more alike.
 *
 * <p>Weights are tuned for cycling/running power workouts where duration and TSS
 * are the dominant intent signals; the block-structure and title features break
 * ties between sessions of similar load.
 */
public final class SessionSimilarityScorer {

    // Sum to 1.0
    private static final double W_DURATION = 0.25;
    private static final double W_TSS = 0.20;
    private static final double W_IF = 0.15;
    private static final double W_POWER = 0.15;
    private static final double W_DISTANCE = 0.10;
    private static final double W_BLOCKS = 0.10;
    private static final double W_TITLE = 0.05;

    private static final Set<String> TITLE_STOPWORDS = Set.of(
            "the", "a", "an", "and", "ride", "run", "swim", "workout", "session", "training");

    private SessionSimilarityScorer() {}

    /**
     * @return similarity 0..100; 100 means structurally identical along the scored dimensions.
     */
    public static int score(CompletedSession seed, CompletedSession candidate) {
        if (seed == null || candidate == null) return 0;
        double distance = 0.0;

        distance += W_DURATION * normalizedAbsDiff(
                seed.getTotalDurationSeconds(), candidate.getTotalDurationSeconds());
        distance += W_TSS * normalizedAbsDiff(seed.getTss(), candidate.getTss());
        distance += W_IF * clip(absDiff(seed.getIntensityFactor(), candidate.getIntensityFactor()) / 0.5);
        distance += W_POWER * normalizedAbsDiff(
                preferredPower(seed), preferredPower(candidate));
        distance += W_DISTANCE * normalizedAbsDiff(seed.getTotalDistance(), candidate.getTotalDistance());
        distance += W_BLOCKS * (1.0 - blockSignatureJaccard(seed, candidate));
        distance += W_TITLE * (1.0 - titleJaccard(seed.getTitle(), candidate.getTitle()));

        double similarity = Math.max(0.0, 1.0 - distance);
        return (int) Math.round(similarity * 100);
    }

    /** NP when available, fall back to avg power. */
    private static Double preferredPower(CompletedSession s) {
        if (s.getNormalizedPower() != null && s.getNormalizedPower() > 0) return s.getNormalizedPower();
        if (s.getAvgPower() > 0) return s.getAvgPower();
        return null;
    }

    /** abs(Δ) / max(seed, candidate). Returns 0 when both null/zero, 1 when only one side is missing. */
    private static double normalizedAbsDiff(Number a, Number b) {
        Double av = a == null ? null : a.doubleValue();
        Double bv = b == null ? null : b.doubleValue();
        if (av == null && bv == null) return 0.0;
        if (av == null || bv == null) return 1.0;
        double max = Math.max(Math.abs(av), Math.abs(bv));
        if (max <= 0.0) return 0.0;
        return clip(Math.abs(av - bv) / max);
    }

    private static double absDiff(Double a, Double b) {
        if (a == null && b == null) return 0.0;
        if (a == null || b == null) return 0.5; // partial penalty when one side missing
        return Math.abs(a - b);
    }

    private static double clip(double v) {
        if (v < 0.0) return 0.0;
        if (v > 1.0) return 1.0;
        return v;
    }

    /** Jaccard similarity of block-type multisets, collapsed to type counts. */
    private static double blockSignatureJaccard(CompletedSession a, CompletedSession b) {
        Set<String> seedSig = blockSignature(a.getBlockSummaries());
        Set<String> candSig = blockSignature(b.getBlockSummaries());
        if (seedSig.isEmpty() && candSig.isEmpty()) return 1.0;
        if (seedSig.isEmpty() || candSig.isEmpty()) return 0.0;
        Set<String> intersect = new HashSet<>(seedSig);
        intersect.retainAll(candSig);
        Set<String> union = new HashSet<>(seedSig);
        union.addAll(candSig);
        return (double) intersect.size() / union.size();
    }

    /** Each token = {@code TYPE:count} so two sessions with 3xINTERVAL look alike but a 1xINTERVAL doesn't match a 5xINTERVAL exactly. */
    private static Set<String> blockSignature(List<CompletedSession.BlockSummary> blocks) {
        Set<String> sig = new HashSet<>();
        if (blocks == null) return sig;
        int[] counts = new int[16]; // arbitrary small bucket per unique type
        java.util.Map<String, Integer> typeCounts = new java.util.HashMap<>();
        for (var b : blocks) {
            if (b.type() == null) continue;
            typeCounts.merge(b.type(), 1, Integer::sum);
        }
        typeCounts.forEach((type, count) -> sig.add(type + ":" + bucket(count)));
        return sig;
    }

    /** Coarse bucketing so 4 vs 5 intervals still match. */
    private static int bucket(int count) {
        if (count <= 1) return 1;
        if (count <= 3) return 2;
        if (count <= 6) return 3;
        return 4;
    }

    /** Token Jaccard over lower-cased, stopword-filtered title words. */
    static double titleJaccard(String a, String b) {
        Set<String> ta = titleTokens(a);
        Set<String> tb = titleTokens(b);
        if (ta.isEmpty() && tb.isEmpty()) return 1.0;
        if (ta.isEmpty() || tb.isEmpty()) return 0.0;
        Set<String> intersect = new HashSet<>(ta);
        intersect.retainAll(tb);
        Set<String> union = new HashSet<>(ta);
        union.addAll(tb);
        return (double) intersect.size() / union.size();
    }

    private static Set<String> titleTokens(String title) {
        if (title == null) return Set.of();
        Set<String> tokens = new HashSet<>(Arrays.asList(
                title.toLowerCase(Locale.ROOT).split("[^\\p{IsAlphabetic}0-9]+")));
        tokens.remove("");
        tokens.removeAll(TITLE_STOPWORDS);
        return tokens;
    }
}
