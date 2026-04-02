package com.koval.trainingplannerbackend.mcp;

import com.koval.trainingplannerbackend.auth.SecurityUtils;
import com.koval.trainingplannerbackend.training.history.AnalyticsService;
import com.koval.trainingplannerbackend.training.history.AnalyticsService.PmcDataPoint;
import com.koval.trainingplannerbackend.training.history.CompletedSession;
import com.koval.trainingplannerbackend.training.history.CompletedSessionRepository;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

/**
 * MCP tool adapter for workout history and performance analytics.
 */
@Service
public class McpHistoryTools {

    private final CompletedSessionRepository sessionRepository;
    private final AnalyticsService analyticsService;

    public McpHistoryTools(CompletedSessionRepository sessionRepository,
                           AnalyticsService analyticsService) {
        this.sessionRepository = sessionRepository;
        this.analyticsService = analyticsService;
    }

    @Tool(description = "Get the user's most recent completed workout sessions. Returns metrics like duration, average power, heart rate, TSS (Training Stress Score), and IF (Intensity Factor).")
    public List<SessionSummary> getRecentSessions(
            @ToolParam(description = "Maximum number of sessions to return (default 10)") Integer limit) {
        String userId = SecurityUtils.getCurrentUserId();
        int effectiveLimit = (limit != null && limit > 0) ? Math.min(limit, 50) : 10;
        return sessionRepository.findByUserIdOrderByCompletedAtDesc(userId).stream()
                .limit(effectiveLimit)
                .map(SessionSummary::from)
                .toList();
    }

    @Tool(description = "Get completed workout sessions within a specific date range.")
    public List<SessionSummary> getSessionsByDateRange(
            @ToolParam(description = "Start date inclusive (YYYY-MM-DD)") LocalDate from,
            @ToolParam(description = "End date inclusive (YYYY-MM-DD)") LocalDate to) {
        String userId = SecurityUtils.getCurrentUserId();
        return sessionRepository.findByUserIdAndCompletedAtBetween(
                        userId, LocalDateTime.of(from, LocalTime.MIN), LocalDateTime.of(to, LocalTime.MAX))
                .stream()
                .map(SessionSummary::from)
                .toList();
    }

    @Tool(description = "Get Performance Management Chart (PMC) data for a date range. Returns daily CTL (Chronic Training Load / fitness), ATL (Acute Training Load / fatigue), and TSB (Training Stress Balance / form) values. Useful for analyzing training load progression.")
    public List<PmcDataPoint> getPmcData(
            @ToolParam(description = "Start date (YYYY-MM-DD)") LocalDate from,
            @ToolParam(description = "End date (YYYY-MM-DD)") LocalDate to) {
        String userId = SecurityUtils.getCurrentUserId();
        return analyticsService.generatePmc(userId, from, to);
    }

    public record SessionSummary(String id, String title, String sportType, String completedAt,
                                  int durationSeconds, double avgPower, double avgHR,
                                  Double tss, Double intensityFactor) {
        public static SessionSummary from(CompletedSession s) {
            return new SessionSummary(
                    s.getId(), s.getTitle(), s.getSportType(),
                    s.getCompletedAt() != null ? s.getCompletedAt().toString() : null,
                    s.getTotalDurationSeconds(), s.getAvgPower(), s.getAvgHR(),
                    s.getTss(), s.getIntensityFactor());
        }
    }
}
