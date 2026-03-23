package com.koval.trainingplannerbackend.training.history;

import com.koval.trainingplannerbackend.auth.User;
import com.koval.trainingplannerbackend.auth.UserRepository;
import com.koval.trainingplannerbackend.training.metrics.TssCalculator;
import com.koval.trainingplannerbackend.training.model.SportType;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.*;

@Service
public class AnalyticsService {

    private static final int CTL_TIME_CONSTANT = 42;
    private static final int ATL_TIME_CONSTANT = 7;
    private static final double K_CTL = 1.0 - Math.exp(-1.0 / CTL_TIME_CONSTANT);
    private static final double K_ATL = 1.0 - Math.exp(-1.0 / ATL_TIME_CONSTANT);

    private final CompletedSessionRepository sessionRepository;
    private final UserRepository userRepository;

    public record PmcDataPoint(LocalDate date, double ctl, double atl,
            double tsb, double dailyTss, Map<String, Double> sportTss, boolean predicted) {
    }

    public AnalyticsService(CompletedSessionRepository sessionRepository, UserRepository userRepository) {
        this.sessionRepository = sessionRepository;
        this.userRepository = userRepository;
    }

    /**
     * Compute TSS and IF for a session using sport-type-appropriate formulas:
     * - CYCLING: power-based TSS using FTP
     * - RUNNING: speed-based rTSS using functionalThresholdPace
     * - SWIMMING: speed-based sTSS using criticalSwimSpeed
     */
    public void computeAndAttachMetrics(CompletedSession session, User user) {
        if (user == null) return;
        if (session.getTotalDurationSeconds() <= 0) return;

        SportType sport = SportType.fromString(session.getSportType());
        OptionalDouble ifOpt = computeIntensityFactor(session, user, sport);

        if (ifOpt.isEmpty() || ifOpt.getAsDouble() <= 0) {
            // RPE fallback — use heuristic when no sensor data available
            if (session.getRpe() != null && session.getRpe() > 0) {
                double intensity = session.getRpe() / 10.0;
                double tss = TssCalculator.computeTss(session.getTotalDurationSeconds(), intensity);
                session.setIntensityFactor(Math.round(intensity * 1000.0) / 1000.0);
                session.setTss(Math.round(tss * 10.0) / 10.0);
            }
            return;
        }

        double intensityFactor = ifOpt.getAsDouble();
        double tss = TssCalculator.computeTss(session.getTotalDurationSeconds(), intensityFactor);
        session.setIntensityFactor(Math.round(intensityFactor * 1000.0) / 1000.0);
        session.setTss(Math.round(tss * 10.0) / 10.0);
    }

    private OptionalDouble computeIntensityFactor(CompletedSession session, User user, SportType sport) {
        return switch (sport) {
            case CYCLING -> {
                int ftp = orZero(user.getFtp());
                yield (ftp > 0 && session.getAvgPower() > 0)
                        ? OptionalDouble.of(session.getAvgPower() / (double) ftp)
                        : OptionalDouble.empty();
            }
            case RUNNING -> {
                int ftPaceSec = orZero(user.getFunctionalThresholdPace());
                yield (ftPaceSec > 0 && session.getAvgSpeed() > 0)
                        ? OptionalDouble.of(session.getAvgSpeed() / (1000.0 / ftPaceSec))
                        : OptionalDouble.empty();
            }
            case SWIMMING -> {
                int cssSec = orZero(user.getCriticalSwimSpeed());
                yield (cssSec > 0 && session.getAvgSpeed() > 0)
                        ? OptionalDouble.of(session.getAvgSpeed() / (100.0 / cssSec))
                        : OptionalDouble.empty();
            }
            case BRICK -> {
                int ftp = orZero(user.getFtp());
                if (ftp > 0 && session.getAvgPower() > 0) {
                    yield OptionalDouble.of(session.getAvgPower() / (double) ftp);
                }
                int ftPaceSec = orZero(user.getFunctionalThresholdPace());
                yield (ftPaceSec > 0 && session.getAvgSpeed() > 0)
                        ? OptionalDouble.of(session.getAvgSpeed() / (1000.0 / ftPaceSec))
                        : OptionalDouble.empty();
            }
        };
    }

    private static int orZero(Integer val) {
        return val != null ? val : 0;
    }

    /**
     * For each block where distanceMeters is 0, estimate distance from
     * durationSeconds × session-level avgSpeed.
     */
    public void computeBlockDistances(CompletedSession session) {
        if (session.getBlockSummaries() == null || session.getAvgSpeed() <= 0) return;

        session.setBlockSummaries(session.getBlockSummaries().stream()
                .map(b -> (b.distanceMeters() == null || b.distanceMeters() <= 0) && b.durationSeconds() > 0
                        ? new CompletedSession.BlockSummary(
                                b.label(), b.type(), b.durationSeconds(),
                                b.targetPower(), b.actualPower(), b.actualCadence(), b.actualHR(),
                                Math.round(b.durationSeconds() * session.getAvgSpeed() * 10.0) / 10.0)
                        : b)
                .toList());
    }

    /**
     * Load all sessions for a user in chronological order, recompute CTL/ATL/TSB
     * via EMA,
     * and save the final values on the User document.
     */
    public void recomputeAndSaveUserLoad(String userId) {
        userRepository.findById(userId).ifPresent(user -> {
            List<CompletedSession> sessions = sessionRepository.findByUserIdOrderByCompletedAtAsc(userId);
            if (sessions.isEmpty())
                return;

            Map<LocalDate, Map<String, Double>> dailyTssMap = buildDailyTssMap(sessions);
            LocalDate firstDate = sessions.get(0).getCompletedAt().toLocalDate();
            EmaState state = runEma(new EmaState(0, 0), firstDate, LocalDate.now(), dailyTssMap);

            user.setCtl(Math.round(state.ctl() * 10.0) / 10.0);
            user.setAtl(Math.round(state.atl() * 10.0) / 10.0);
            user.setTsb(Math.round((state.ctl() - state.atl()) * 10.0) / 10.0);
            userRepository.save(user);
        });
    }

    /**
     * Generate PMC data points for the given date range, including decay on rest
     * days.
     */
    public List<PmcDataPoint> generatePmc(String userId, LocalDate from, LocalDate to) {
        List<CompletedSession> sessions = sessionRepository.findByUserIdOrderByCompletedAtAsc(userId);
        Map<LocalDate, Map<String, Double>> dailyTssMap = buildDailyTssMap(sessions);

        // Start EMA from the earliest session or from 'from' date, whichever is earlier
        LocalDate startDate = sessions.isEmpty() ? from : sessions.get(0).getCompletedAt().toLocalDate();
        if (from.isBefore(startDate)) startDate = from;

        // Warm up EMA before the requested window
        EmaState state = runEma(new EmaState(0, 0), startDate, from.minusDays(1), dailyTssMap);

        // Collect results for the requested window
        List<PmcDataPoint> result = new ArrayList<>();
        LocalDate cursor = from;
        while (!cursor.isAfter(to)) {
            Map<String, Double> sports = dailyTssMap.getOrDefault(cursor, Map.of());
            double dailyTss = sports.values().stream().mapToDouble(Double::doubleValue).sum();
            state = state.step(dailyTss);
            result.add(new PmcDataPoint(cursor,
                    Math.round(state.ctl() * 10.0) / 10.0,
                    Math.round(state.atl() * 10.0) / 10.0,
                    Math.round((state.ctl() - state.atl()) * 10.0) / 10.0,
                    dailyTss, sports, false));
            cursor = cursor.plusDays(1);
        }

        return result;
    }

    private record EmaState(double ctl, double atl) {
        EmaState step(double dailyTss) {
            return new EmaState(ctl + (dailyTss - ctl) * K_CTL, atl + (dailyTss - atl) * K_ATL);
        }
    }

    private EmaState runEma(EmaState state, LocalDate from, LocalDate to,
                            Map<LocalDate, Map<String, Double>> dailyTssMap) {
        LocalDate cursor = from;
        while (!cursor.isAfter(to)) {
            double dailyTss = dailyTssMap.getOrDefault(cursor, Map.of())
                    .values().stream().mapToDouble(Double::doubleValue).sum();
            state = state.step(dailyTss);
            cursor = cursor.plusDays(1);
        }
        return state;
    }

    private Map<LocalDate, Map<String, Double>> buildDailyTssMap(List<CompletedSession> sessions) {
        Map<LocalDate, Map<String, Double>> map = new HashMap<>();
        for (CompletedSession s : sessions) {
            if (s.getTss() == null || s.getCompletedAt() == null)
                continue;
            String sport = s.getSportType() != null ? s.getSportType() : "CYCLING";
            // Swimming excluded from PMC: CSS-based TSS is not directly comparable to
            // cycling/running TSS and would distort CTL/ATL/TSB load tracking.
            if ("SWIMMING".equals(sport))
                continue;
            LocalDate date = s.getCompletedAt().toLocalDate();

            map.computeIfAbsent(date, k -> new HashMap<>())
                    .merge(sport, s.getTss(), Double::sum);
        }
        return map;
    }
}
