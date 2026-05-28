package com.koval.trainingplannerbackend.training.history.compare;

import com.koval.trainingplannerbackend.training.history.CompletedSession;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * Multi-session comparison payload. The first session in {@link #sessions} is the
 * reference; per-block deltas are computed relative to it.
 */
public record ComparisonReport(
        String sportType,
        List<SessionEntry> sessions,
        List<AlignedBlock> alignedBlocks,
        List<MetricDelta> biggestDeltas) {

    /** One column in the comparison view. */
    public record SessionEntry(
            String id,
            String title,
            LocalDateTime completedAt,
            int totalDurationSeconds,
            Double tss,
            Double intensityFactor,
            Double normalizedPower,
            Double avgPower,
            Double avgHR,
            Double avgCadence,
            Double avgSpeed,
            Double totalDistance,
            Integer rpe,
            List<CompletedSession.BlockSummary> blockSummaries,
            Map<Integer, Double> powerCurve) {
    }

    /** A logical block, aligned across sessions by label (or ordinal fallback). */
    public record AlignedBlock(
            String label,
            String type,
            List<BlockCell> perSession) {

        public record BlockCell(
                String sessionId,
                boolean present,
                int durationSeconds,
                double targetPower,
                double actualPower,
                double actualHR,
                double actualCadence) {
        }
    }

    /** Largest signed deltas vs the reference session, useful for surfacing highlights. */
    public record MetricDelta(
            String sessionId,
            String metric,
            double referenceValue,
            double sessionValue,
            double delta,
            String reason) {
    }
}
