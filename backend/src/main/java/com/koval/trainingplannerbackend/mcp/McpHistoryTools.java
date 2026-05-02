package com.koval.trainingplannerbackend.mcp;

import com.koval.trainingplannerbackend.auth.SecurityUtils;
import com.koval.trainingplannerbackend.training.history.AnalyticsService;
import com.koval.trainingplannerbackend.training.history.AnalyticsService.PmcDataPoint;
import com.koval.trainingplannerbackend.training.history.CompletedSession;
import com.koval.trainingplannerbackend.training.history.CompletedSession.BlockSummary;
import com.koval.trainingplannerbackend.training.history.CompletedSessionRepository;
import com.koval.trainingplannerbackend.training.history.SessionService;
import com.koval.trainingplannerbackend.training.metrics.PowerCurveService;
import com.koval.trainingplannerbackend.training.metrics.PowerCurveService.VolumeEntry;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * MCP tool adapter for workout history, power curves, volume aggregation and session
 * mutation. Delegates to {@link SessionService}, {@link PowerCurveService} and
 * {@link AnalyticsService}.
 */
@Service
public class McpHistoryTools {

    private final CompletedSessionRepository sessionRepository;
    private final AnalyticsService analyticsService;
    private final SessionService sessionService;
    private final PowerCurveService powerCurveService;

    public McpHistoryTools(CompletedSessionRepository sessionRepository,
                           AnalyticsService analyticsService,
                           SessionService sessionService,
                           PowerCurveService powerCurveService) {
        this.sessionRepository = sessionRepository;
        this.analyticsService = analyticsService;
        this.sessionService = sessionService;
        this.powerCurveService = powerCurveService;
    }

    @Tool(description = "Get the user's most recent completed workout sessions. Returns metrics like duration, average power, heart rate, TSS (Training Stress Score), and IF (Intensity Factor).")
    public List<SessionSummary> getRecentSessions(
            @ToolParam(description = "Maximum number of sessions to return (default 10, max 50)") Integer limit) {
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

    @Tool(description = "Get full detail of a single completed session: title, sport, duration, average power/HR/cadence, TSS, IF, RPE, total distance, whether a FIT file is attached, and the per-block summary list. Use this when the user asks 'how was my last ride' or wants a deep dive on a specific session.")
    public SessionDetail getSessionDetail(
            @ToolParam(description = "Completed session ID") String sessionId) {
        String userId = SecurityUtils.getCurrentUserId();
        CompletedSession s = sessionService.getSession(userId, sessionId)
                .orElseThrow(() -> new IllegalArgumentException("Session not found: " + sessionId));
        return SessionDetail.from(s);
    }

    @Tool(description = "Get the mean-maximal power curve (best average watts per duration) for a single completed cycling session. Durations are 5s, 15s, 30s, 1m, 2m, 5m, 10m, 20m, 30m, 1h, 1.5h, 2h. Computed lazily from the FIT file on first request, then cached. Returns an empty map for non-cycling sessions or sessions without power data.")
    public Map<Integer, Double> getSessionPowerCurve(
            @ToolParam(description = "Completed session ID") String sessionId) {
        String userId = SecurityUtils.getCurrentUserId();
        return powerCurveService.getSessionPowerCurve(sessionId, userId);
    }

    @Tool(description = "Get the user's best mean-maximal power curve across all cycling sessions in a date range. Combines the highest average watts achieved at each standard duration (5s through 2h). Use to spot fitness peaks or compare two periods (e.g. last 30 days vs last 90 days).")
    public Map<Integer, Double> getBestPowerCurve(
            @ToolParam(description = "Start date inclusive (YYYY-MM-DD)") LocalDate from,
            @ToolParam(description = "End date inclusive (YYYY-MM-DD)") LocalDate to) {
        String userId = SecurityUtils.getCurrentUserId();
        return powerCurveService.getBestPowerCurve(userId, from, to);
    }

    @Tool(description = "Get the user's all-time personal records — the best average power ever held over each standard duration (5s, 15s, 30s, 1m, 2m, 5m, 10m, 20m, 30m, 1h, 1.5h, 2h).")
    public Map<Integer, Double> getPersonalRecords() {
        String userId = SecurityUtils.getCurrentUserId();
        return powerCurveService.getPersonalRecords(userId);
    }

    @Tool(description = "Get aggregated training volume per week or month: total TSS, total duration in seconds, total distance in meters, and TSS broken down by sport. Use groupBy='week' or 'month'.")
    public List<VolumeEntry> getVolume(
            @ToolParam(description = "Start date inclusive (YYYY-MM-DD)") LocalDate from,
            @ToolParam(description = "End date inclusive (YYYY-MM-DD)") LocalDate to,
            @ToolParam(description = "Aggregation period: 'week' or 'month'") String groupBy) {
        String userId = SecurityUtils.getCurrentUserId();
        return powerCurveService.computeVolume(userId, from, to, groupBy);
    }

    @Tool(description = "Get the per-block breakdown of a completed session: each interval/steady/warmup block with its duration, target power, actual power, average HR and cadence. Useful for analysing structured workout execution quality.")
    public List<BlockSummary> getSessionBlocks(
            @ToolParam(description = "Completed session ID") String sessionId) {
        String userId = SecurityUtils.getCurrentUserId();
        CompletedSession s = sessionService.getSession(userId, sessionId)
                .orElseThrow(() -> new IllegalArgumentException("Session not found: " + sessionId));
        return s.getBlockSummaries() != null ? s.getBlockSummaries() : List.of();
    }

    @Tool(description = "Set the Rate of Perceived Exertion (RPE, 1-10 scale) on a completed session. Use after the user reports how hard a session felt.")
    public SessionDetail setSessionRpe(
            @ToolParam(description = "Completed session ID") String sessionId,
            @ToolParam(description = "RPE on 1-10 scale (1 very easy, 10 maximal)") int rpe) {
        if (rpe < 1 || rpe > 10) throw new IllegalArgumentException("RPE must be between 1 and 10.");
        String userId = SecurityUtils.getCurrentUserId();
        Map<String, Object> patch = new HashMap<>();
        patch.put("rpe", rpe);
        CompletedSession updated = sessionService.patchSession(sessionId, patch, userId);
        return SessionDetail.from(updated);
    }

    @Tool(description = "Link a completed session to a previously scheduled workout, marking that workout as completed in the calendar.")
    public SessionDetail linkSessionToScheduled(
            @ToolParam(description = "Completed session ID") String sessionId,
            @ToolParam(description = "Scheduled workout ID to link to") String scheduledWorkoutId) {
        String userId = SecurityUtils.getCurrentUserId();
        CompletedSession s = sessionService.linkSessionToSchedule(sessionId, scheduledWorkoutId, userId);
        return SessionDetail.from(s);
    }

    @Tool(description = "Permanently delete a completed session and any FIT file attached to it. This cannot be undone.")
    public String deleteSession(
            @ToolParam(description = "Completed session ID to delete") String sessionId) {
        String userId = SecurityUtils.getCurrentUserId();
        boolean removed = sessionService.deleteSession(sessionId, userId);
        return removed ? "Session deleted." : "Error: session not found or not owned by user.";
    }

    public record SessionSummary(String id, String title, String sportType, String completedAt,
                                  int durationSeconds, double avgPower, double avgHR,
                                  Double tss, Double intensityFactor) {
        public static SessionSummary from(CompletedSession s) {
            return new SessionSummary(
                    s.getId(), s.getTitle(), s.getSportType(),
                    Optional.ofNullable(s.getCompletedAt()).map(Object::toString).orElse(null),
                    s.getTotalDurationSeconds(), s.getAvgPower(), s.getAvgHR(),
                    s.getTss(), s.getIntensityFactor());
        }
    }

    public record SessionDetail(String id, String title, String sportType, String completedAt,
                                 int durationSeconds, Integer movingTimeSeconds,
                                 double avgPower, double avgHR, double avgCadence, double avgSpeed,
                                 Double totalDistance, Double tss, Double intensityFactor,
                                 Integer rpe, boolean hasFitFile, String scheduledWorkoutId,
                                 String clubSessionId, int blockCount) {
        public static SessionDetail from(CompletedSession s) {
            return new SessionDetail(
                    s.getId(), s.getTitle(), s.getSportType(),
                    Optional.ofNullable(s.getCompletedAt()).map(Object::toString).orElse(null),
                    s.getTotalDurationSeconds(), s.getMovingTimeSeconds(),
                    s.getAvgPower(), s.getAvgHR(), s.getAvgCadence(), s.getAvgSpeed(),
                    s.getTotalDistance(), s.getTss(), s.getIntensityFactor(),
                    s.getRpe(), s.getFitFileId() != null,
                    s.getScheduledWorkoutId(), s.getClubSessionId(),
                    Optional.ofNullable(s.getBlockSummaries()).map(List::size).orElse(0));
        }
    }
}
