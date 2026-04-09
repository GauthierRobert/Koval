package com.koval.trainingplannerbackend.training.metrics;

import com.koval.trainingplannerbackend.training.history.CompletedSession;
import com.koval.trainingplannerbackend.training.history.CompletedSessionRepository;
import com.mongodb.client.gridfs.model.GridFSFile;
import org.bson.types.ObjectId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.gridfs.GridFsOperations;
import org.springframework.data.mongodb.gridfs.GridFsResource;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Power curve analysis and volume aggregation across completed sessions.
 *
 * <p>Curves are computed server-side from the FIT files stored in GridFS. The first request
 * for a given session parses the FIT, runs the mean-max algorithm, and persists the result
 * to {@link CompletedSession#powerCurve}. Subsequent requests are served from the cache or
 * the persisted field. Only cycling sessions are processed.
 */
@Service
public class PowerCurveService {

    private static final Logger log = LoggerFactory.getLogger(PowerCurveService.class);

    /** Standard durations (seconds) reported in the curve. Capped at 2h. */
    public static final int[] CURVE_DURATIONS = {
            5, 15, 30, 60, 120, 300, 600, 1200, 1800, 3600, 5400, 7200
    };

    private final CompletedSessionRepository sessionRepository;
    private final GridFsOperations gridFsOperations;

    public PowerCurveService(CompletedSessionRepository sessionRepository,
                             GridFsOperations gridFsOperations) {
        this.sessionRepository = sessionRepository;
        this.gridFsOperations = gridFsOperations;
    }

    /**
     * Best power curve across cycling sessions in a date range. Lazily fills in any
     * sessions whose curve has not yet been computed.
     */
    public Map<Integer, Double> getBestPowerCurve(String userId, LocalDate from, LocalDate to) {
        List<CompletedSession> sessions = sessionRepository.findByUserIdAndCompletedAtBetween(
                userId, from.atStartOfDay(), to.plusDays(1).atStartOfDay());

        Map<Integer, Double> bestCurve = initializeEmptyCurve();

        for (CompletedSession session : sessions) {
            if (!isCycling(session)) continue;
            Map<Integer, Double> sessionCurve = ensureSessionCurve(session);
            if (sessionCurve == null || sessionCurve.isEmpty()) continue;
            mergeBestValues(bestCurve, sessionCurve);
        }

        bestCurve.entrySet().removeIf(e -> e.getValue() <= 0);
        return bestCurve;
    }

    /**
     * Power curve for a single completed session. Computed lazily from the FIT file
     * on first access and persisted on the session document for subsequent calls.
     * Returns an empty map for non-cycling sessions or when no power data is available.
     *
     * @param sessionId the completed session identifier
     * @param userId    the owning user identifier
     */
    @Cacheable(value = "sessionPowerCurves", key = "#sessionId", unless = "#result.isEmpty()")
    public Map<Integer, Double> getSessionPowerCurve(String sessionId, String userId) {
        return sessionRepository.findByIdAndUserId(sessionId, userId)
                .map(this::ensureSessionCurve)
                .orElse(Map.of());
    }

    /**
     * Force-recompute the curve for a session and clear its cache entry. Call this when
     * the FIT file attached to a session changes (upload/replace).
     */
    @CacheEvict(value = "sessionPowerCurves", key = "#sessionId")
    public void invalidateSessionPowerCurve(String sessionId) {
        sessionRepository.findById(sessionId).ifPresent(s -> {
            s.setPowerCurve(null);
            sessionRepository.save(s);
        });
    }

    /** All-time personal records (best average power by duration) for a user. */
    public Map<Integer, Double> getPersonalRecords(String userId) {
        return getBestPowerCurve(userId, LocalDate.of(2020, 1, 1), LocalDate.now());
    }

    // ── Volume aggregation ──────────────────────────────────────────

    /**
     * Aggregated training volume for a single time period (week or month).
     */
    public record VolumeEntry(String period, double totalTss, long totalDurationSeconds,
                              double totalDistanceMeters, Map<String, Double> sportTss) {
    }

    /** Aggregate training volume by week or month. */
    public List<VolumeEntry> computeVolume(String userId, LocalDate from, LocalDate to, String groupBy) {
        List<CompletedSession> sessions = sessionRepository.findByUserIdAndCompletedAtBetween(
                userId, from.atStartOfDay(), to.plusDays(1).atStartOfDay());

        Map<String, List<CompletedSession>> grouped = groupSessionsByPeriod(sessions, groupBy);

        List<VolumeEntry> result = new ArrayList<>();
        for (var entry : grouped.entrySet()) {
            result.add(aggregateVolumeEntry(entry.getKey(), entry.getValue()));
        }
        return result;
    }

    // ── Power curve computation ─────────────────────────────────────

    /**
     * Return the persisted power curve for a session, computing and saving it from the
     * FIT file on first access. Returns an empty map for non-cycling sessions, sessions
     * without a FIT file, or sessions whose FIT contains no power data.
     */
    private Map<Integer, Double> ensureSessionCurve(CompletedSession session) {
        if (!isCycling(session)) return Map.of();

        Map<Integer, Double> existing = session.getPowerCurve();
        if (existing != null && !existing.isEmpty()) return existing;

        if (session.getFitFileId() == null) return Map.of();

        Map<Integer, Double> curve = computePowerCurveFromFit(session.getFitFileId());
        if (curve.isEmpty()) return Map.of();

        session.setPowerCurve(curve);
        sessionRepository.save(session);
        return curve;
    }

    private Map<Integer, Double> computePowerCurveFromFit(String fitFileId) {
        try {
            GridFSFile gridFile = gridFsOperations.findOne(
                    Query.query(Criteria.where("_id").is(new ObjectId(fitFileId))));
            if (gridFile == null) return Map.of();
            GridFsResource resource = gridFsOperations.getResource(gridFile);
            byte[] bytes = resource.getInputStream().readAllBytes();
            List<Integer> samples = FitPowerExtractor.extractPower(bytes);
            return computeMeanMaxCurve(samples);
        } catch (Exception e) {
            log.warn("Failed to compute power curve for fitFileId={}: {}", fitFileId, e.getMessage());
            return Map.of();
        }
    }

    /**
     * Mean-maximal power curve via prefix sums. For each duration {@code w} in
     * {@link #CURVE_DURATIONS}, returns the highest moving average over any window of
     * {@code w} consecutive samples (assumed 1 Hz).
     */
    static Map<Integer, Double> computeMeanMaxCurve(List<Integer> samples) {
        int n = samples.size();
        if (n == 0) return Map.of();
        long[] cum = new long[n + 1];
        for (int i = 0; i < n; i++) cum[i + 1] = cum[i] + samples.get(i);

        Map<Integer, Double> curve = new LinkedHashMap<>();
        for (int dur : CURVE_DURATIONS) {
            if (dur > n) continue;
            long best = 0;
            for (int i = 0; i + dur <= n; i++) {
                long sum = cum[i + dur] - cum[i];
                if (sum > best) best = sum;
            }
            if (best > 0) curve.put(dur, (double) best / dur);
        }
        return curve;
    }

    // ── Private helpers ─────────────────────────────────────────────

    private static boolean isCycling(CompletedSession session) {
        // Treat null as cycling for backwards compatibility with legacy sessions where
        // sportType was not yet stored — every other sport is excluded explicitly.
        String sport = session.getSportType();
        return sport == null || "CYCLING".equalsIgnoreCase(sport);
    }

    private Map<Integer, Double> initializeEmptyCurve() {
        Map<Integer, Double> curve = new LinkedHashMap<>();
        for (int dur : CURVE_DURATIONS) {
            curve.put(dur, 0.0);
        }
        return curve;
    }

    private void mergeBestValues(Map<Integer, Double> bestCurve, Map<Integer, Double> sessionCurve) {
        for (int dur : CURVE_DURATIONS) {
            Double val = sessionCurve.get(dur);
            if (val != null && val > bestCurve.getOrDefault(dur, 0.0)) {
                bestCurve.put(dur, val);
            }
        }
    }

    private Map<String, List<CompletedSession>> groupSessionsByPeriod(List<CompletedSession> sessions, String groupBy) {
        Map<String, List<CompletedSession>> grouped = new LinkedHashMap<>();
        for (CompletedSession s : sessions) {
            if (s.getCompletedAt() == null) continue;
            LocalDate date = s.getCompletedAt().toLocalDate();
            String key = "month".equals(groupBy)
                    ? date.getYear() + "-" + String.format("%02d", date.getMonthValue())
                    : date.getYear() + "-W" + String.format("%02d", getIsoWeek(date));
            grouped.computeIfAbsent(key, k -> new ArrayList<>()).add(s);
        }
        return grouped;
    }

    private VolumeEntry aggregateVolumeEntry(String period, List<CompletedSession> sessions) {
        double totalTss = 0;
        long totalDuration = 0;
        double totalDistance = 0;
        Map<String, Double> sportTss = new HashMap<>();

        for (CompletedSession s : sessions) {
            double tss = s.getTss() != null ? s.getTss() : 0;
            totalTss += tss;
            totalDuration += s.getTotalDurationSeconds();
            totalDistance += s.getTotalDistance() != null ? s.getTotalDistance() : 0;
            String sport = s.getSportType() != null ? s.getSportType() : "CYCLING";
            sportTss.merge(sport, tss, Double::sum);
        }

        return new VolumeEntry(period, Math.round(totalTss * 10.0) / 10.0,
                totalDuration, totalDistance, sportTss);
    }

    private int getIsoWeek(LocalDate date) {
        return date.get(java.time.temporal.IsoFields.WEEK_OF_WEEK_BASED_YEAR);
    }
}
