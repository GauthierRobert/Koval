package com.koval.trainingplannerbackend.training.history.compare;

import com.koval.trainingplannerbackend.training.history.CompletedSession;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** Pure-logic tests for {@link SessionSimilarityScorer} — no Spring context. */
class SessionSimilarityScorerTest {

    private CompletedSession session(String title, String sport, int durationSec, double tss,
                                     double intensityFactor, double np,
                                     List<CompletedSession.BlockSummary> blocks) {
        CompletedSession s = new CompletedSession();
        s.setTitle(title);
        s.setSportType(sport);
        s.setTotalDurationSeconds(durationSec);
        s.setTss(tss);
        s.setIntensityFactor(intensityFactor);
        s.setNormalizedPower(np);
        s.setAvgPower(np);
        s.setBlockSummaries(blocks);
        return s;
    }

    private CompletedSession.BlockSummary block(String label, String type, int seconds) {
        return new CompletedSession.BlockSummary(label, type, seconds, 200, 210, 90, 150, null);
    }

    @Test
    void identicalSessions_score100() {
        var a = session("Tempo Endurance", "CYCLING", 5400, 87, 0.78, 215,
                List.of(block("Warmup", "WARMUP", 600), block("Tempo", "STEADY", 4200), block("Cooldown", "COOLDOWN", 600)));
        var b = session("Tempo Endurance", "CYCLING", 5400, 87, 0.78, 215,
                List.of(block("Warmup", "WARMUP", 600), block("Tempo", "STEADY", 4200), block("Cooldown", "COOLDOWN", 600)));

        assertThat(SessionSimilarityScorer.score(a, b)).isEqualTo(100);
    }

    @Test
    void verySimilarSessions_scoreAbove80() {
        var seed = session("Tempo Endurance", "CYCLING", 5400, 87, 0.78, 215,
                List.of(block("Warmup", "WARMUP", 600), block("Tempo", "STEADY", 4200), block("Cooldown", "COOLDOWN", 600)));
        var close = session("Tempo Endurance #2", "CYCLING", 5280, 84, 0.76, 208,
                List.of(block("Warmup", "WARMUP", 600), block("Tempo", "STEADY", 4080), block("Cooldown", "COOLDOWN", 600)));

        assertThat(SessionSimilarityScorer.score(seed, close)).isGreaterThanOrEqualTo(80);
    }

    @Test
    void differentLoad_scoresLower() {
        var tempo = session("Tempo Endurance", "CYCLING", 5400, 87, 0.78, 215,
                List.of(block("Warmup", "WARMUP", 600), block("Tempo", "STEADY", 4200)));
        var recovery = session("Recovery spin", "CYCLING", 1800, 22, 0.55, 120,
                List.of(block("Free", "FREE", 1800)));

        int score = SessionSimilarityScorer.score(tempo, recovery);
        assertThat(score).isLessThan(50);
    }

    @Test
    void blockSignatureBuckets_intervalCountsCloseEnough() {
        var a = session("Intervals", "CYCLING", 3600, 70, 0.85, 250,
                List.of(block("VO2 #1", "INTERVAL", 180), block("VO2 #2", "INTERVAL", 180),
                        block("VO2 #3", "INTERVAL", 180), block("VO2 #4", "INTERVAL", 180)));
        var b = session("Intervals", "CYCLING", 3600, 70, 0.85, 250,
                List.of(block("VO2 #1", "INTERVAL", 180), block("VO2 #2", "INTERVAL", 180),
                        block("VO2 #3", "INTERVAL", 180), block("VO2 #4", "INTERVAL", 180),
                        block("VO2 #5", "INTERVAL", 180)));

        // Same bucket (4-6 intervals) → block signature Jaccard = 1
        assertThat(SessionSimilarityScorer.score(a, b)).isGreaterThanOrEqualTo(95);
    }

    @Test
    void titleJaccard_filtersStopwords() {
        double exact = SessionSimilarityScorer.titleJaccard("Tempo Endurance", "Tempo Endurance");
        double oneSideStripped = SessionSimilarityScorer.titleJaccard("Ride", "Tempo Endurance");
        double mixed = SessionSimilarityScorer.titleJaccard("Tempo Endurance Ride", "Tempo Endurance Run");

        assertThat(exact).isEqualTo(1.0);
        // "Ride" is a stopword → first side becomes empty → 0 overlap.
        assertThat(oneSideStripped).isEqualTo(0.0);
        // "Tempo" and "Endurance" overlap; "Ride"/"Run" are stopwords; Jaccard = 2/2.
        assertThat(mixed).isGreaterThan(0.5);
    }

    @Test
    void nullSession_scoresZero() {
        assertThat(SessionSimilarityScorer.score(null, null)).isZero();
    }
}
