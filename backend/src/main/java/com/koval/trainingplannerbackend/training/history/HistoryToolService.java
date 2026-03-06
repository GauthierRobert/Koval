package com.koval.trainingplannerbackend.training.history;

import com.koval.trainingplannerbackend.training.history.AnalyticsService.PmcDataPoint;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

/**
 * AI-facing tool service for workout history and performance analytics.
 */
@Service
public class HistoryToolService {

    private final CompletedSessionRepository sessionRepository;
    private final AnalyticsService analyticsService;

    public HistoryToolService(CompletedSessionRepository sessionRepository,
                              AnalyticsService analyticsService) {
        this.sessionRepository = sessionRepository;
        this.analyticsService = analyticsService;
    }

    @Tool(description = "Get a user's most recent completed workout sessions. Returns title, date, duration, avg power/HR, TSS, IF.")
    public List<SessionSummary> getRecentSessions(
            @ToolParam(description = "User ID") String userId,
            @ToolParam(description = "Maximum number of sessions to return (e.g. 5, 10)") int limit) {
        return sessionRepository.findByUserIdOrderByCompletedAtDesc(userId).stream()
                .limit(limit)
                .map(SessionSummary::from)
                .toList();
    }

    @Tool(description = "Get a user's completed sessions within a date range.")
    public List<SessionSummary> getSessionsByDateRange(
            @ToolParam(description = "User ID") String userId,
            @ToolParam(description = "Start date (YYYY-MM-DD, inclusive)") LocalDate from,
            @ToolParam(description = "End date (YYYY-MM-DD, inclusive)") LocalDate to) {
        return sessionRepository.findByUserIdAndCompletedAtBetween(
                        userId,
                        LocalDateTime.of(from, LocalTime.MIN),
                        LocalDateTime.of(to, LocalTime.MAX)).stream()
                .map(SessionSummary::from)
                .toList();
    }

    @Tool(description = "Get Performance Management Chart (PMC) data: CTL (fitness), ATL (fatigue), TSB (form) for a date range.")
    public List<PmcDataPoint> getPmcData(
            @ToolParam(description = "User ID") String userId,
            @ToolParam(description = "Start date (YYYY-MM-DD)") LocalDate from,
            @ToolParam(description = "End date (YYYY-MM-DD)") LocalDate to) {
        return analyticsService.generatePmc(userId, from, to);
    }

    /**
     * Lean summary to minimize token usage.
     */
    public record SessionSummary(
            String id,
            String title,
            String sportType,
            String completedAt,
            int durationSeconds,
            double avgPower,
            double avgHR,
            Double tss,
            Double intensityFactor) {

        public static SessionSummary from(CompletedSession s) {
            return new SessionSummary(
                    s.getId(),
                    s.getTitle(),
                    s.getSportType(),
                    s.getCompletedAt() != null ? s.getCompletedAt().toString() : null,
                    s.getTotalDurationSeconds(),
                    s.getAvgPower(),
                    s.getAvgHR(),
                    s.getTss(),
                    s.getIntensityFactor());
        }
    }
}
