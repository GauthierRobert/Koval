package com.koval.trainingplannerbackend.training.history;

import com.koval.trainingplannerbackend.auth.User;
import com.koval.trainingplannerbackend.auth.UserRepository;
import com.koval.trainingplannerbackend.training.model.SportType;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class AnalyticsService {

    private final CompletedSessionRepository sessionRepository;
    private final UserRepository userRepository;

    public record PmcDataPoint(LocalDate date, double ctl, double atl,
                               double tsb, double dailyTss, boolean predicted) {}

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
        double durationHours = session.getTotalDurationSeconds() / 3600.0;
        if (durationHours <= 0) return;

        SportType sport = parseSportType(session.getSportType());
        double intensityFactor = 0;

        if (sport == SportType.CYCLING) {
            int ftp = user.getFtp() != null ? user.getFtp() : 0;
            if (ftp <= 0 || session.getAvgPower() <= 0) return;
            intensityFactor = session.getAvgPower() / (double) ftp;

        } else if (sport == SportType.RUNNING) {
            int ftPaceSec = user.getFunctionalThresholdPace() != null ? user.getFunctionalThresholdPace() : 0;
            if (ftPaceSec <= 0 || session.getAvgSpeed() <= 0) return;
            // functionalThresholdPace is sec/km → threshold speed in m/s = 1000 / sec/km
            double thresholdSpeed = 1000.0 / ftPaceSec;
            intensityFactor = session.getAvgSpeed() / thresholdSpeed;

        } else if (sport == SportType.SWIMMING) {
            int cssSec = user.getCriticalSwimSpeed() != null ? user.getCriticalSwimSpeed() : 0;
            if (cssSec <= 0 || session.getAvgSpeed() <= 0) return;
            // criticalSwimSpeed is sec/100m → CSS in m/s = 100 / sec/100m
            double cssSpeed = 100.0 / cssSec;
            intensityFactor = session.getAvgSpeed() / cssSpeed;

        } else {
            // BRICK: prefer power if available, fall back to running pace
            int ftp = user.getFtp() != null ? user.getFtp() : 0;
            if (ftp > 0 && session.getAvgPower() > 0) {
                intensityFactor = session.getAvgPower() / (double) ftp;
            } else {
                int ftPaceSec = user.getFunctionalThresholdPace() != null ? user.getFunctionalThresholdPace() : 0;
                if (ftPaceSec <= 0 || session.getAvgSpeed() <= 0) return;
                intensityFactor = session.getAvgSpeed() / (1000.0 / ftPaceSec);
            }
        }

        if (intensityFactor <= 0) return;
        double tss = durationHours * intensityFactor * intensityFactor * 100.0;
        session.setIntensityFactor(Math.round(intensityFactor * 1000.0) / 1000.0);
        session.setTss(Math.round(tss * 10.0) / 10.0);
    }

    private SportType parseSportType(String sportType) {
        if (sportType == null) return SportType.CYCLING;
        try {
            return SportType.valueOf(sportType);
        } catch (IllegalArgumentException e) {
            return SportType.CYCLING;
        }
    }

    /**
     * Load all sessions for a user in chronological order, recompute CTL/ATL/TSB via EMA,
     * and save the final values on the User document.
     */
    public void recomputeAndSaveUserLoad(String userId) {
        userRepository.findById(userId).ifPresent(user -> {
            List<CompletedSession> sessions = sessionRepository.findByUserIdOrderByCompletedAtAsc(userId);
            if (sessions.isEmpty()) return;

            LocalDate firstDate = sessions.get(0).getCompletedAt().toLocalDate();
            LocalDate today = LocalDate.now();

            // Build a map of date -> total TSS for that day
            Map<LocalDate, Double> dailyTssMap = buildDailyTssMap(sessions);

            double ctl = 0.0;
            double atl = 0.0;
            double kCTL = 1.0 - Math.exp(-1.0 / 42.0);
            double kATL = 1.0 - Math.exp(-1.0 / 7.0);

            LocalDate cursor = firstDate;
            while (!cursor.isAfter(today)) {
                double dailyTss = dailyTssMap.getOrDefault(cursor, 0.0);
                ctl = ctl + (dailyTss - ctl) * kCTL;
                atl = atl + (dailyTss - atl) * kATL;
                cursor = cursor.plusDays(1);
            }

            user.setCtl(Math.round(ctl * 10.0) / 10.0);
            user.setAtl(Math.round(atl * 10.0) / 10.0);
            user.setTsb(Math.round((ctl - atl) * 10.0) / 10.0);
            userRepository.save(user);
        });
    }

    /**
     * Generate PMC data points for the given date range, including decay on rest days.
     */
    public List<PmcDataPoint> generatePmc(String userId, LocalDate from, LocalDate to) {
        List<CompletedSession> sessions = sessionRepository.findByUserIdOrderByCompletedAtAsc(userId);

        // Build daily TSS map from all sessions (not just the window)
        Map<LocalDate, Double> dailyTssMap = buildDailyTssMap(sessions);

        // Start EMA from the earliest session or from 'from' date, whichever is earlier
        LocalDate startDate = sessions.isEmpty() ? from : sessions.get(0).getCompletedAt().toLocalDate();
        if (from.isBefore(startDate)) startDate = from;

        double ctl = 0.0;
        double atl = 0.0;
        double kCTL = 1.0 - Math.exp(-1.0 / 42.0);
        double kATL = 1.0 - Math.exp(-1.0 / 7.0);

        // Warm up EMA from startDate to the day before 'from'
        LocalDate cursor = startDate;
        while (cursor.isBefore(from)) {
            double dailyTss = dailyTssMap.getOrDefault(cursor, 0.0);
            ctl = ctl + (dailyTss - ctl) * kCTL;
            atl = atl + (dailyTss - atl) * kATL;
            cursor = cursor.plusDays(1);
        }

        // Collect results for the requested window
        List<PmcDataPoint> result = new ArrayList<>();
        cursor = from;
        while (!cursor.isAfter(to)) {
            double dailyTss = dailyTssMap.getOrDefault(cursor, 0.0);
            ctl = ctl + (dailyTss - ctl) * kCTL;
            atl = atl + (dailyTss - atl) * kATL;
            double tsb = ctl - atl;
            result.add(new PmcDataPoint(
                    cursor,
                    Math.round(ctl * 10.0) / 10.0,
                    Math.round(atl * 10.0) / 10.0,
                    Math.round(tsb * 10.0) / 10.0,
                    dailyTss,
                    false
            ));
            cursor = cursor.plusDays(1);
        }

        return result;
    }

    private Map<LocalDate, Double> buildDailyTssMap(List<CompletedSession> sessions) {
        Map<LocalDate, Double> map = new HashMap<>();
        for (CompletedSession s : sessions) {
            if (s.getTss() == null || s.getCompletedAt() == null) continue;
            LocalDate date = s.getCompletedAt().toLocalDate();
            map.merge(date, s.getTss(), Double::sum);
        }
        return map;
    }
}
