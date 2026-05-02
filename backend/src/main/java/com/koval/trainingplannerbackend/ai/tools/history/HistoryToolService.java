package com.koval.trainingplannerbackend.ai.tools.history;

import com.koval.trainingplannerbackend.auth.SecurityUtils;
import com.koval.trainingplannerbackend.training.history.AnalyticsService;
import com.koval.trainingplannerbackend.training.history.AnalyticsService.PmcDataPoint;
import com.koval.trainingplannerbackend.training.history.CompletedSession;
import com.koval.trainingplannerbackend.training.history.CompletedSessionRepository;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;

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

    /** Returns the most recent completed sessions for a user, limited to the given count. */
    @Tool(description = "Get recent completed sessions for a user.")
    public List<SessionSummary> getRecentSessions(
            @ToolParam(description = "Max sessions to return") int limit,
            ToolContext context) {
        String userId = SecurityUtils.getUserId(context);
        return sessionRepository.findByUserIdOrderByCompletedAtDesc(userId).stream()
                .limit(limit)
                .map(SessionSummary::from)
                .toList();
    }

    /** Returns completed sessions for a user within the given inclusive date range. */
    @Tool(description = "Get a user's completed sessions within a date range.")
    public List<SessionSummary> getSessionsByDateRange(
            @ToolParam(description = "Start date (YYYY-MM-DD, inclusive)") LocalDate from,
            @ToolParam(description = "End date (YYYY-MM-DD, inclusive)") LocalDate to,
            ToolContext context) {
        String userId = SecurityUtils.getUserId(context);
        return sessionRepository.findByUserIdAndCompletedAtBetween(
                        userId,
                        LocalDateTime.of(from, LocalTime.MIN),
                        LocalDateTime.of(to, LocalTime.MAX)).stream()
                .map(SessionSummary::from)
                .toList();
    }

    /** Generates PMC data points (CTL, ATL, TSB) for the given date range. */
    @Tool(description = "Get PMC data (CTL, ATL, TSB) for a date range.")
    public List<PmcDataPoint> getPmcData(
            @ToolParam(description = "Start date") LocalDate from,
            @ToolParam(description = "End date") LocalDate to,
            ToolContext context) {
        String userId = SecurityUtils.getUserId(context);
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
                    Optional.ofNullable(s.getCompletedAt()).map(LocalDateTime::toString).orElse(null),
                    s.getTotalDurationSeconds(),
                    s.getAvgPower(),
                    s.getAvgHR(),
                    s.getTss(),
                    s.getIntensityFactor());
        }
    }
}
