package com.koval.trainingplannerbackend.training.history;

import com.koval.trainingplannerbackend.training.model.Training;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Produces a deterministic, first-pass <em>suggestion</em> for how a completed session aligned with
 * its scheduled workout, by comparing what the athlete actually did against the planned targets.
 *
 * <p>This is intentionally a simple ratio model — it pre-fills the rating modal so a coach or athlete
 * has a starting number to accept or override. The authoritative, nuanced score (including subtle
 * zone-repartition judgement) is set by a human or by an AI client via the MCP tools; that logic is
 * NOT encoded here. The estimate is never persisted on its own.
 *
 * <p>Each dimension contributes only when both the planned and actual figures are available; the
 * remaining weights are renormalised over the dimensions that did contribute.
 */
@Component
public class AlignmentEstimator {

    // Relative importance of each dimension. TSS captures duration×intensity together, so it leads.
    private static final double W_TSS = 0.40;
    private static final double W_IF = 0.25;
    private static final double W_BLOCK_POWER = 0.20;
    private static final double W_DURATION = 0.15;

    /**
     * @param session the completed session (actuals)
     * @param planned the Training the session was scheduled from; may be {@code null} if the plan is
     *                no longer available, in which case only self-contained dimensions are used
     * @return estimate with an overall percentage and a per-dimension breakdown
     */
    public AlignmentEstimate estimate(CompletedSession session, Training planned) {
        List<Factor> factors = new ArrayList<>();

        if (planned != null) {
            addRatio(factors, "tss", W_TSS,
                    toDouble(planned.getEstimatedTss()), session.getTss());
            addRatio(factors, "if", W_IF,
                    planned.getEstimatedIf(), session.getIntensityFactor());
            addRatio(factors, "duration", W_DURATION,
                    toDouble(planned.getEstimatedDurationSeconds()),
                    (double) AnalyticsService.loadDurationSeconds(session));
        }
        addRatio(factors, "blockPower", W_BLOCK_POWER, blockPowerRatio(session));

        return new AlignmentEstimate(weightedScore(factors), factors);
    }

    /** Average of actual/target power across blocks that declared a target. Null when none do. */
    private Double blockPowerRatio(CompletedSession session) {
        if (session.getBlockSummaries() == null) return null;
        double sum = 0;
        int n = 0;
        for (CompletedSession.BlockSummary b : session.getBlockSummaries()) {
            if (b.targetPower() > 0 && b.actualPower() > 0) {
                sum += b.actualPower() / b.targetPower();
                n++;
            }
        }
        return n > 0 ? sum / n : null;
    }

    private void addRatio(List<Factor> factors, String name, double weight, Double planned, Double actual) {
        if (planned == null || planned <= 0 || actual == null || actual <= 0) return;
        addRatio(factors, name, weight, actual / planned, planned, actual);
    }

    /** Overload for a pre-computed ratio (e.g. block power) with no single planned/actual pair. */
    private void addRatio(List<Factor> factors, String name, double weight, Double ratio) {
        if (ratio == null || ratio <= 0) return;
        factors.add(new Factor(name, weight, null, null, percent(ratio)));
    }

    private void addRatio(List<Factor> factors, String name, double weight, double ratio,
                          Double planned, Double actual) {
        factors.add(new Factor(name, weight, planned, actual, percent(ratio)));
    }

    private int weightedScore(List<Factor> factors) {
        double weighted = 0;
        double totalWeight = 0;
        for (Factor f : factors) {
            weighted += f.ratioPercent() * f.weight();
            totalWeight += f.weight();
        }
        if (totalWeight <= 0) return 100; // no data → assume on-plan rather than mislead
        return (int) Math.round(weighted / totalWeight);
    }

    private static int percent(double ratio) {
        return (int) Math.round(ratio * 100);
    }

    private static Double toDouble(Integer v) {
        return v == null ? null : v.doubleValue();
    }

    /**
     * Deterministic suggestion for a session's alignment.
     *
     * @param score   overall percentage (100 = on plan)
     * @param factors per-dimension contributions that fed the score
     */
    public record AlignmentEstimate(int score, List<Factor> factors) {}

    /**
     * One dimension of the estimate.
     *
     * @param name         dimension key: tss | if | duration | blockPower
     * @param weight       relative weight applied to this dimension
     * @param planned      planned reference value (null for blockPower, which is already a ratio)
     * @param actual       actual measured value (null for blockPower)
     * @param ratioPercent actual/planned as a percentage
     */
    public record Factor(String name, double weight, Double planned, Double actual, int ratioPercent) {}
}
