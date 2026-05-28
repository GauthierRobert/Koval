package com.koval.trainingplannerbackend.training.history.compare;

import com.koval.trainingplannerbackend.config.exceptions.ValidationException;
import com.koval.trainingplannerbackend.training.history.CompletedSession;
import com.koval.trainingplannerbackend.training.history.CompletedSessionRepository;
import com.koval.trainingplannerbackend.training.history.SessionService;
import com.koval.trainingplannerbackend.training.metrics.PowerCurveService;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Orchestrates "find similar sessions" and "compare N sessions" use cases.
 * Always scopes reads to sessions the caller can see ({@link SessionService#getSession}).
 */
@Service
public class SessionComparisonService {

    /** Max sessions allowed in a single comparison call; keeps charts legible and payload bounded. */
    public static final int MAX_COMPARE = 4;
    public static final int DEFAULT_SIMILAR_LIMIT = 10;

    private final CompletedSessionRepository repository;
    private final SessionService sessionService;
    private final PowerCurveService powerCurveService;

    public SessionComparisonService(CompletedSessionRepository repository,
                                    SessionService sessionService,
                                    PowerCurveService powerCurveService) {
        this.repository = repository;
        this.sessionService = sessionService;
        this.powerCurveService = powerCurveService;
    }

    /** Same-sport sessions ranked by similarity, seed excluded. */
    public List<SimilarSessionDto> findSimilar(String userId, String seedId, int limit) {
        CompletedSession seed = sessionService.getSession(userId, seedId)
                .orElseThrow(() -> new ValidationException("Session not found: " + seedId));
        if (seed.getSportType() == null) return List.of();

        List<CompletedSession> candidates =
                repository.findByUserIdOrderByCompletedAtDesc(seed.getUserId());

        int cap = Math.max(1, Math.min(limit, 50));
        return candidates.stream()
                .filter(c -> !Objects.equals(c.getId(), seed.getId()))
                .filter(c -> seed.getSportType().equals(c.getSportType()))
                .map(c -> toSimilarDto(c, SessionSimilarityScorer.score(seed, c)))
                .sorted(Comparator.comparingInt(SimilarSessionDto::similarityPercent).reversed())
                .limit(cap)
                .toList();
    }

    /**
     * Build the full N-way comparison. The first id is the reference for deltas.
     * Enforces same sport across the set.
     */
    public ComparisonReport compare(String userId, List<String> sessionIds) {
        if (sessionIds == null || sessionIds.size() < 2) {
            throw new ValidationException("compareSessions requires at least 2 session ids");
        }
        if (sessionIds.size() > MAX_COMPARE) {
            throw new ValidationException("compareSessions accepts at most " + MAX_COMPARE + " session ids");
        }

        List<CompletedSession> sessions = new ArrayList<>(sessionIds.size());
        String sport = null;
        for (String id : sessionIds) {
            CompletedSession s = sessionService.getSession(userId, id)
                    .orElseThrow(() -> new ValidationException("Session not found: " + id));
            if (sport == null) sport = s.getSportType();
            else if (!Objects.equals(sport, s.getSportType())) {
                throw new ValidationException("All sessions must share the same sport");
            }
            sessions.add(s);
        }

        List<ComparisonReport.SessionEntry> entries = sessions.stream()
                .map(this::toEntry)
                .toList();

        List<ComparisonReport.AlignedBlock> aligned = alignBlocks(sessions);
        List<ComparisonReport.MetricDelta> deltas = topDeltas(sessions);

        return new ComparisonReport(sport, entries, aligned, deltas);
    }

    private SimilarSessionDto toSimilarDto(CompletedSession s, int similarity) {
        Double power = s.getNormalizedPower() != null && s.getNormalizedPower() > 0
                ? s.getNormalizedPower()
                : (s.getAvgPower() > 0 ? s.getAvgPower() : null);
        return new SimilarSessionDto(
                s.getId(),
                s.getTitle(),
                s.getSportType(),
                s.getCompletedAt(),
                s.getTotalDurationSeconds(),
                s.getTss(),
                s.getIntensityFactor(),
                s.getNormalizedPower(),
                power,
                s.getTotalDistance(),
                similarity);
    }

    private ComparisonReport.SessionEntry toEntry(CompletedSession s) {
        Map<Integer, Double> curve;
        try {
            curve = powerCurveService.getSessionPowerCurve(s.getId(), s.getUserId());
        } catch (RuntimeException ex) {
            curve = Map.of();
        }
        return new ComparisonReport.SessionEntry(
                s.getId(),
                s.getTitle(),
                s.getCompletedAt(),
                s.getTotalDurationSeconds(),
                s.getTss(),
                s.getIntensityFactor(),
                s.getNormalizedPower(),
                s.getAvgPower(),
                s.getAvgHR(),
                s.getAvgCadence(),
                s.getAvgSpeed(),
                s.getTotalDistance(),
                s.getRpe(),
                Optional.ofNullable(s.getBlockSummaries()).orElseGet(List::of),
                curve == null ? Map.of() : curve);
    }

    /** Aligns blocks across sessions by case-insensitive label, falling back to ordinal+type. */
    private List<ComparisonReport.AlignedBlock> alignBlocks(List<CompletedSession> sessions) {
        // Use the reference (first) session's block order as the canonical sequence.
        CompletedSession reference = sessions.get(0);
        List<CompletedSession.BlockSummary> refBlocks =
                Optional.ofNullable(reference.getBlockSummaries()).orElseGet(List::of);
        if (refBlocks.isEmpty()) return List.of();

        List<ComparisonReport.AlignedBlock> out = new ArrayList<>(refBlocks.size());
        for (int i = 0; i < refBlocks.size(); i++) {
            CompletedSession.BlockSummary ref = refBlocks.get(i);
            List<ComparisonReport.AlignedBlock.BlockCell> cells = new ArrayList<>(sessions.size());
            for (CompletedSession s : sessions) {
                CompletedSession.BlockSummary match = findBlockMatch(s, ref, i);
                if (match == null) {
                    cells.add(new ComparisonReport.AlignedBlock.BlockCell(
                            s.getId(), false, 0, 0, 0, 0, 0));
                } else {
                    cells.add(new ComparisonReport.AlignedBlock.BlockCell(
                            s.getId(), true,
                            match.durationSeconds(),
                            match.targetPower(),
                            match.actualPower(),
                            match.actualHR(),
                            match.actualCadence()));
                }
            }
            out.add(new ComparisonReport.AlignedBlock(ref.label(), ref.type(), cells));
        }
        return out;
    }

    private CompletedSession.BlockSummary findBlockMatch(
            CompletedSession s, CompletedSession.BlockSummary ref, int idx) {
        List<CompletedSession.BlockSummary> blocks = s.getBlockSummaries();
        if (blocks == null || blocks.isEmpty()) return null;
        if (ref.label() != null) {
            for (var b : blocks) {
                if (b.label() != null && b.label().equalsIgnoreCase(ref.label())) return b;
            }
        }
        if (idx < blocks.size()) {
            var positional = blocks.get(idx);
            if (Objects.equals(positional.type(), ref.type())) return positional;
        }
        return null;
    }

    /**
     * Picks the largest absolute delta on TSS, NP/avg power, IF, and aerobic HR vs the reference,
     * one row per non-reference session. Skips metrics that are missing on either side.
     */
    private List<ComparisonReport.MetricDelta> topDeltas(List<CompletedSession> sessions) {
        if (sessions.size() < 2) return List.of();
        CompletedSession ref = sessions.get(0);
        List<ComparisonReport.MetricDelta> out = new ArrayList<>();

        for (int i = 1; i < sessions.size(); i++) {
            CompletedSession s = sessions.get(i);
            Map<String, double[]> candidates = new LinkedHashMap<>();
            addIfBothPresent(candidates, "TSS", ref.getTss(), s.getTss());
            addIfBothPresent(candidates, "IF", ref.getIntensityFactor(), s.getIntensityFactor());
            addIfBothPresent(candidates, "Power",
                    refDouble(ref.getNormalizedPower(), ref.getAvgPower()),
                    refDouble(s.getNormalizedPower(), s.getAvgPower()));
            addIfBothPresent(candidates, "Avg HR", ref.getAvgHR(), s.getAvgHR());
            addIfBothPresent(candidates, "Duration",
                    (double) ref.getTotalDurationSeconds(),
                    (double) s.getTotalDurationSeconds());

            String winner = null;
            double winnerScore = 0;
            for (var e : candidates.entrySet()) {
                double[] vals = e.getValue();
                double rel = Math.abs(vals[1] - vals[0]) / Math.max(1e-9, Math.abs(vals[0]));
                if (rel > winnerScore) {
                    winnerScore = rel;
                    winner = e.getKey();
                }
            }
            if (winner == null) continue;
            double[] v = candidates.get(winner);
            String reason = String.format("Largest deviation from reference (%+.1f%%)",
                    100.0 * (v[1] - v[0]) / Math.max(1e-9, Math.abs(v[0])));
            out.add(new ComparisonReport.MetricDelta(
                    s.getId(), winner, v[0], v[1], v[1] - v[0], reason));
        }
        return out;
    }

    private static void addIfBothPresent(Map<String, double[]> out, String key, Double a, Double b) {
        if (a == null || b == null) return;
        out.put(key, new double[]{a, b});
    }

    private static Double refDouble(Double primary, double fallback) {
        if (primary != null && primary > 0) return primary;
        if (fallback > 0) return fallback;
        return null;
    }

}
