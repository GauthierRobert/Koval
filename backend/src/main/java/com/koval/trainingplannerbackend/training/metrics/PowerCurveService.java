package com.koval.trainingplannerbackend.training.metrics;

import com.koval.trainingplannerbackend.training.history.CompletedSession;
import com.koval.trainingplannerbackend.training.history.CompletedSessionRepository;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.*;

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

        Map<Integer, Double> bestCurve = new LinkedHashMap<>();
        for (int dur : CURVE_DURATIONS) {
            bestCurve.put(dur, 0.0);
        }

        for (CompletedSession session : sessions) {
            if (session.getPowerCurve() == null || session.getPowerCurve().isEmpty()) continue;
            for (int dur : CURVE_DURATIONS) {
                Double val = session.getPowerCurve().get(dur);
                if (val != null && val > bestCurve.getOrDefault(dur, 0.0)) {
                    bestCurve.put(dur, val);
                }
            }
        }

        bestCurve.entrySet().removeIf(e -> e.getValue() <= 0);
        return bestCurve;
    }

    /**
     * Get power curve for a single session.
     */
    public Map<Integer, Double> getSessionPowerCurve(String sessionId, String userId) {
        return sessionRepository.findByIdAndUserId(sessionId, userId)
                .map(s -> s.getPowerCurve() != null ? s.getPowerCurve() : Map.<Integer, Double>of())
                .orElse(Map.of());
    }

    /**
     * Store computed power curve for a session (called after frontend parses FIT).
     */
    public void savePowerCurve(String sessionId, String userId, Map<Integer, Double> powerCurve) {
        sessionRepository.findByIdAndUserId(sessionId, userId).ifPresent(session -> {
            session.setPowerCurve(powerCurve);
            sessionRepository.save(session);
        });
    }

    /**
     * All-time personal records (best power by duration).
     */
    public Map<Integer, Double> getPersonalRecords(String userId) {
        return getBestPowerCurve(userId, LocalDate.of(2020, 1, 1), LocalDate.now());
    }

    // ── Volume aggregation ──────────────────────────────────────────

    public record VolumeEntry(String period, double totalTss, long totalDurationSeconds,
                              double totalDistanceMeters, Map<String, Double> sportTss) {
    }

    /**
     * Aggregate training volume by week or month.
     */
    public List<VolumeEntry> computeVolume(String userId, LocalDate from, LocalDate to, String groupBy) {
        List<CompletedSession> sessions = sessionRepository.findByUserIdAndCompletedAtBetween(
                userId, from.atStartOfDay(), to.plusDays(1).atStartOfDay());

        Map<String, List<CompletedSession>> grouped = new LinkedHashMap<>();
        for (CompletedSession s : sessions) {
            if (s.getCompletedAt() == null) continue;
            LocalDate date = s.getCompletedAt().toLocalDate();
            String key = "month".equals(groupBy)
                    ? date.getYear() + "-" + String.format("%02d", date.getMonthValue())
                    : date.getYear() + "-W" + String.format("%02d", getIsoWeek(date));
            grouped.computeIfAbsent(key, k -> new ArrayList<>()).add(s);
        }

        List<VolumeEntry> result = new ArrayList<>();
        for (var entry : grouped.entrySet()) {
            double totalTss = 0;
            long totalDuration = 0;
            double totalDistance = 0;
            Map<String, Double> sportTss = new HashMap<>();

            for (CompletedSession s : entry.getValue()) {
                double tss = s.getTss() != null ? s.getTss() : 0;
                totalTss += tss;
                totalDuration += s.getTotalDurationSeconds();
                totalDistance += s.getTotalDistance() != null ? s.getTotalDistance() : 0;
                String sport = s.getSportType() != null ? s.getSportType() : "CYCLING";
                sportTss.merge(sport, tss, Double::sum);
            }

            result.add(new VolumeEntry(entry.getKey(), Math.round(totalTss * 10.0) / 10.0,
                    totalDuration, totalDistance, sportTss));
        }
        return result;
    }

    private int getIsoWeek(LocalDate date) {
        return date.get(java.time.temporal.IsoFields.WEEK_OF_WEEK_BASED_YEAR);
    }
}
