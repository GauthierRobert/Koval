package com.koval.trainingplannerbackend.training.metrics;

import com.koval.trainingplannerbackend.training.history.CompletedSession;
import com.koval.trainingplannerbackend.training.history.CompletedSessionRepository;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Power curve analysis and volume aggregation across completed sessions.
 */
@Service
public class PowerCurveService {

    // Standard durations for power curve (in seconds)
    public static final int[] CURVE_DURATIONS = {
            5, 15, 30, 60, 120, 300, 600, 1200, 1800, 3600, 5400, 7200
    };

    private final CompletedSessionRepository sessionRepository;

    public PowerCurveService(CompletedSessionRepository sessionRepository) {
        this.sessionRepository = sessionRepository;
    }

    /**
     * Get best power curve across sessions in a date range.
     * Aggregates cached powerCurve data from individual sessions.
     */
    public Map<Integer, Double> getBestPowerCurve(String userId, LocalDate from, LocalDate to) {
        List<CompletedSession> sessions = sessionRepository.findByUserIdAndCompletedAtBetween(
                userId, from.atStartOfDay(), to.plusDays(1).atStartOfDay());

        Map<Integer, Double> bestCurve = initializeEmptyCurve();

        for (CompletedSession session : sessions) {
            if (session.getPowerCurve() == null || session.getPowerCurve().isEmpty()) continue;
            mergeBestValues(bestCurve, session.getPowerCurve());
        }

        bestCurve.entrySet().removeIf(e -> e.getValue() <= 0);
        return bestCurve;
    }

    /**
     * Get the power curve for a single completed session.
     *
     * @param sessionId the completed session identifier
     * @param userId    the owning user identifier
     * @return the session's power curve, or an empty map if unavailable
     */
    @Cacheable(value = "sessionPowerCurves", key = "#sessionId")
    public Map<Integer, Double> getSessionPowerCurve(String sessionId, String userId) {
        return sessionRepository.findByIdAndUserId(sessionId, userId)
                .map(s -> s.getPowerCurve() != null ? s.getPowerCurve() : Map.<Integer, Double>of())
                .orElse(Map.of());
    }

    /**
     * Store a computed power curve for a session (called after the frontend parses a FIT file).
     *
     * @param sessionId  the completed session identifier
     * @param userId     the owning user identifier
     * @param powerCurve map of duration (seconds) to best average power (watts)
     */
    @CacheEvict(value = "sessionPowerCurves", key = "#sessionId")
    public void savePowerCurve(String sessionId, String userId, Map<Integer, Double> powerCurve) {
        sessionRepository.findByIdAndUserId(sessionId, userId).ifPresent(session -> {
            session.setPowerCurve(powerCurve);
            sessionRepository.save(session);
        });
    }

    /**
     * Return all-time personal records (best average power by duration) for a user.
     *
     * @param userId the user identifier
     * @return map of duration (seconds) to best average power (watts)
     */
    public Map<Integer, Double> getPersonalRecords(String userId) {
        return getBestPowerCurve(userId, LocalDate.of(2020, 1, 1), LocalDate.now());
    }

    // ── Volume aggregation ──────────────────────────────────────────

    /**
     * Aggregated training volume for a single time period (week or month).
     *
     * @param period               the period label (e.g. "2026-03" or "2026-W13")
     * @param totalTss             total Training Stress Score for the period
     * @param totalDurationSeconds total duration in seconds
     * @param totalDistanceMeters  total distance in meters
     * @param sportTss             TSS broken down by sport type
     */
    public record VolumeEntry(String period, double totalTss, long totalDurationSeconds,
                              double totalDistanceMeters, Map<String, Double> sportTss) {
    }

    /**
     * Aggregate training volume by week or month.
     */
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

    // ── Private helpers ─────────────────────────────────────────────

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
