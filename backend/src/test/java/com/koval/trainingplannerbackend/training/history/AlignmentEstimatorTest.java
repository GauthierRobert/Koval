package com.koval.trainingplannerbackend.training.history;

import com.koval.trainingplannerbackend.training.model.CyclingTraining;
import com.koval.trainingplannerbackend.training.model.Training;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** Pure-logic tests for the deterministic alignment estimate (no Spring context). */
class AlignmentEstimatorTest {

    private final AlignmentEstimator estimator = new AlignmentEstimator();

    private Training planned(Integer tss, Double intensityFactor, Integer durationSeconds) {
        Training t = new CyclingTraining();
        t.setEstimatedTss(tss);
        t.setEstimatedIf(intensityFactor);
        t.setEstimatedDurationSeconds(durationSeconds);
        return t;
    }

    private CompletedSession session(Double tss, Double intensityFactor, int durationSeconds) {
        CompletedSession s = new CompletedSession();
        s.setTss(tss);
        s.setIntensityFactor(intensityFactor);
        s.setTotalDurationSeconds(durationSeconds);
        return s;
    }

    @Test
    void exactlyOnPlan_scores100() {
        var estimate = estimator.estimate(
                session(100.0, 0.80, 3600), planned(100, 0.80, 3600));
        assertThat(estimate.score()).isEqualTo(100);
        assertThat(estimate.factors()).extracting(AlignmentEstimator.Factor::name)
                .contains("tss", "if", "duration");
    }

    @Test
    void exceedingPlan_scoresAbove100() {
        var estimate = estimator.estimate(
                session(120.0, 0.90, 3600), planned(100, 0.80, 3600));
        assertThat(estimate.score()).isGreaterThan(100);
    }

    @Test
    void underPlan_scoresBelow100() {
        var estimate = estimator.estimate(
                session(70.0, 0.70, 2400), planned(100, 0.85, 3600));
        assertThat(estimate.score()).isLessThan(100);
    }

    @Test
    void noPlanButBlocksPresent_usesBlockPowerOnly() {
        CompletedSession s = session(null, null, 3600);
        s.setBlockSummaries(List.of(
                new CompletedSession.BlockSummary("Int 1", "INTERVAL", 300, 200, 220, 90, 160, null),
                new CompletedSession.BlockSummary("Int 2", "INTERVAL", 300, 200, 180, 90, 160, null)));

        var estimate = estimator.estimate(s, null);

        // (220/200 + 180/200) / 2 = 1.0 → 100%
        assertThat(estimate.score()).isEqualTo(100);
        assertThat(estimate.factors()).singleElement()
                .extracting(AlignmentEstimator.Factor::name).isEqualTo("blockPower");
    }

    @Test
    void noUsableData_defaultsTo100() {
        var estimate = estimator.estimate(session(null, null, 0), null);
        assertThat(estimate.score()).isEqualTo(100);
        assertThat(estimate.factors()).isEmpty();
    }
}
