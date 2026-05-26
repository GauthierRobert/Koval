package com.koval.trainingplannerbackend.mcp;

import com.koval.trainingplannerbackend.auth.SecurityUtils;
import com.koval.trainingplannerbackend.config.Provenance;
import com.koval.trainingplannerbackend.training.history.AiAnalysis;
import com.koval.trainingplannerbackend.training.history.AiAnalysisService;
import com.koval.trainingplannerbackend.training.history.AlignmentScore;
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
import org.springframework.data.domain.PageRequest;
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
    private final AiAnalysisService aiAnalysisService;
    private final McpAccessResolver accessResolver;

    public McpHistoryTools(CompletedSessionRepository sessionRepository,
                           AnalyticsService analyticsService,
                           SessionService sessionService,
                           PowerCurveService powerCurveService,
                           AiAnalysisService aiAnalysisService,
                           McpAccessResolver accessResolver) {
        this.sessionRepository = sessionRepository;
        this.analyticsService = analyticsService;
        this.sessionService = sessionService;
        this.powerCurveService = powerCurveService;
        this.aiAnalysisService = aiAnalysisService;
        this.accessResolver = accessResolver;
    }

    @Tool(description = "Get completed workout sessions. mode='recent' returns the latest N sessions (use limit; default 10, max 50). mode='range' returns all sessions between from and to (YYYY-MM-DD, inclusive). Returns metrics like duration, average power, heart rate, TSS (Training Stress Score) and IF (Intensity Factor). Omit athleteId for your own sessions; pass a coached athlete's id to read theirs (requires COACH role and a coaching relationship).")
    public List<SessionSummary> getSessions(
            @ToolParam(description = "Query mode: 'recent' for the latest sessions, or 'range' for a date window. Defaults to 'recent'.") String mode,
            @ToolParam(description = "Start date inclusive (YYYY-MM-DD). Required when mode='range'.") LocalDate from,
            @ToolParam(description = "End date inclusive (YYYY-MM-DD). Required when mode='range'.") LocalDate to,
            @ToolParam(description = "Max sessions to return when mode='recent' (default 10, max 50). Ignored for 'range'.") Integer limit,
            @ToolParam(required = false, description = "Coached athlete's user ID. Omit/null for your own sessions.") String athleteId) {
        String userId = accessResolver.resolve(athleteId).subjectId();
        String effectiveMode = (mode == null || mode.isBlank()) ? "recent" : mode.trim().toLowerCase();
        return switch (effectiveMode) {
            case "recent" -> {
                int effectiveLimit = (limit != null && limit > 0) ? Math.min(limit, 50) : 10;
                yield sessionRepository
                        .findByUserIdOrderByCompletedAtDesc(userId, PageRequest.of(0, effectiveLimit))
                        .stream()
                        .map(SessionSummary::from)
                        .toList();
            }
            case "range" -> {
                if (from == null || to == null) {
                    throw new IllegalArgumentException("mode='range' requires both from and to dates (YYYY-MM-DD).");
                }
                yield sessionRepository.findByUserIdAndCompletedAtBetween(
                                userId, LocalDateTime.of(from, LocalTime.MIN), LocalDateTime.of(to, LocalTime.MAX))
                        .stream()
                        .map(SessionSummary::from)
                        .toList();
            }
            default -> throw new IllegalArgumentException("mode must be 'recent' or 'range'.");
        };
    }

    @Tool(description = "Get Performance Management Chart (PMC) data for a date range. Returns daily CTL (Chronic Training Load / fitness), ATL (Acute Training Load / fatigue), and TSB (Training Stress Balance / form) values. Useful for analyzing training load progression. Omit athleteId for your own PMC; pass a coached athlete's id to read theirs (requires COACH role and a coaching relationship).")
    public List<PmcDataPoint> getPmcData(
            @ToolParam(description = "Start date (YYYY-MM-DD)") LocalDate from,
            @ToolParam(description = "End date (YYYY-MM-DD)") LocalDate to,
            @ToolParam(required = false, description = "Coached athlete's user ID. Omit/null for your own PMC.") String athleteId) {
        String userId = accessResolver.resolve(athleteId).subjectId();
        return analyticsService.generatePmc(userId, from, to);
    }

    @Tool(description = "Get full detail of a single completed session: title, sport, duration, average power/HR/cadence, TSS, IF, RPE, total distance, whether a FIT file is attached, and the per-block summary list. Use this when the user asks 'how was my last ride' or wants a deep dive on a specific session. Omit athleteId for your own session; pass a coached athlete's id to read theirs (requires COACH role and a coaching relationship).")
    public SessionDetail getSessionDetail(
            @ToolParam(description = "Completed session ID") String sessionId,
            @ToolParam(required = false, description = "Coached athlete's user ID. Omit/null for your own session.") String athleteId) {
        String userId = accessResolver.resolve(athleteId).subjectId();
        CompletedSession s = sessionService.getSession(userId, sessionId)
                .orElseThrow(() -> new IllegalArgumentException("Session not found: " + sessionId));
        return SessionDetail.from(s);
    }

    @Tool(description = "Get the best mean-maximal power curve across all cycling sessions in a date range. Combines the highest average watts achieved at each standard duration (5s through 2h). Use to spot fitness peaks or compare two periods (e.g. last 30 days vs last 90 days). Omit athleteId for your own curve; pass a coached athlete's id to read theirs (requires COACH role and a coaching relationship).")
    public Map<Integer, Double> getBestPowerCurve(
            @ToolParam(description = "Start date inclusive (YYYY-MM-DD)") LocalDate from,
            @ToolParam(description = "End date inclusive (YYYY-MM-DD)") LocalDate to,
            @ToolParam(required = false, description = "Coached athlete's user ID. Omit/null for your own curve.") String athleteId) {
        String userId = accessResolver.resolve(athleteId).subjectId();
        return powerCurveService.getBestPowerCurve(userId, from, to);
    }

    @Tool(description = "Get aggregated training volume per week or month: total TSS, total duration in seconds, total distance in meters, and TSS broken down by sport. Use groupBy='week' or 'month'. Omit athleteId for your own volume; pass a coached athlete's id to read theirs (requires COACH role and a coaching relationship).")
    public List<VolumeEntry> getVolume(
            @ToolParam(description = "Start date inclusive (YYYY-MM-DD)") LocalDate from,
            @ToolParam(description = "End date inclusive (YYYY-MM-DD)") LocalDate to,
            @ToolParam(description = "Aggregation period: 'week' or 'month'") String groupBy,
            @ToolParam(required = false, description = "Coached athlete's user ID. Omit/null for your own volume.") String athleteId) {
        String userId = accessResolver.resolve(athleteId).subjectId();
        return powerCurveService.computeVolume(userId, from, to, groupBy);
    }

    @Tool(description = "Get the per-block breakdown of a completed session: each interval/steady/warmup block with its duration, target power, actual power, average HR and cadence. Useful for analysing structured workout execution quality. Omit athleteId for your own session; pass a coached athlete's id to read theirs (requires COACH role and a coaching relationship).")
    public List<BlockSummary> getSessionBlocks(
            @ToolParam(description = "Completed session ID") String sessionId,
            @ToolParam(required = false, description = "Coached athlete's user ID. Omit/null for your own session.") String athleteId) {
        String userId = accessResolver.resolve(athleteId).subjectId();
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

    @Tool(description = "Permanently delete a completed session and any FIT file attached to it. This cannot be undone.")
    public String deleteSession(
            @ToolParam(description = "Completed session ID to delete") String sessionId) {
        String userId = SecurityUtils.getCurrentUserId();
        boolean removed = sessionService.deleteSession(sessionId, userId);
        return removed ? "Session deleted." : "Error: session not found or not owned by user.";
    }

    @Tool(description = "Publish an AI-generated analysis of a completed workout session back to Koval. " +
            "Attaches a short summary plus a full markdown body (and optional highlights) to the session " +
            "so it appears in the athlete's history with an AI-source badge. Use after analyzing a session " +
            "in detail — the user does NOT have to copy/paste it themselves. Callable by the session owner " +
            "or by a coach managing the athlete.")
    public AiAnalysisSummary publishSessionAnalysis(
            @ToolParam(description = "Completed session ID to attach the analysis to") String sessionId,
            @ToolParam(description = "Short 1-3 line summary shown in history lists (max 500 chars)") String summary,
            @ToolParam(description = "Full analysis body in markdown (max 20000 chars)") String body,
            @ToolParam(description = "Optional bullet list of 3-6 key takeaways (max 10 items)") List<String> highlights) {
        String userId = SecurityUtils.getCurrentUserId();
        AiAnalysis saved = aiAnalysisService.publish(
                userId, sessionId, summary, body, highlights, Provenance.mcp());
        return AiAnalysisSummary.from(saved);
    }

    public record AiAnalysisSummary(String id, String sessionId, String athleteId, String authorId,
                                     String summary, String createdAt, String source) {
        public static AiAnalysisSummary from(AiAnalysis a) {
            return new AiAnalysisSummary(
                    a.getId(), a.getSessionId(), a.getAthleteId(), a.getAuthorId(),
                    a.getSummary(),
                    Optional.ofNullable(a.getCreatedAt()).map(Object::toString).orElse(null),
                    a.getProvenance() != null ? a.getProvenance().source() : null);
        }
    }

    public record SessionSummary(String id, String title, String sportType, String completedAt,
                                  int durationSeconds, double avgPower, double avgHR,
                                  Double tss, Double intensityFactor, Integer alignmentScore) {
        public static SessionSummary from(CompletedSession s) {
            return new SessionSummary(
                    s.getId(), s.getTitle(), s.getSportType(),
                    Optional.ofNullable(s.getCompletedAt()).map(Object::toString).orElse(null),
                    s.getTotalDurationSeconds(), s.getAvgPower(), s.getAvgHR(),
                    s.getTss(), s.getIntensityFactor(), effectiveAlignment(s));
        }
    }

    public record SessionDetail(String id, String title, String sportType, String completedAt,
                                 int durationSeconds, Integer movingTimeSeconds,
                                 double avgPower, double avgHR, double avgCadence, double avgSpeed,
                                 Double totalDistance, Double tss, Double intensityFactor,
                                 Integer rpe, boolean hasFitFile, String scheduledWorkoutId,
                                 String clubSessionId, int blockCount,
                                 Integer alignmentScore, Integer athleteAlignmentScore,
                                 Integer coachAlignmentScore, String coachAlignmentSource) {
        public static SessionDetail from(CompletedSession s) {
            AlignmentScore a = s.getAlignmentScore();
            return new SessionDetail(
                    s.getId(), s.getTitle(), s.getSportType(),
                    Optional.ofNullable(s.getCompletedAt()).map(Object::toString).orElse(null),
                    s.getTotalDurationSeconds(), s.getMovingTimeSeconds(),
                    s.getAvgPower(), s.getAvgHR(), s.getAvgCadence(), s.getAvgSpeed(),
                    s.getTotalDistance(), s.getTss(), s.getIntensityFactor(),
                    s.getRpe(), s.getFitFileId() != null,
                    s.getScheduledWorkoutId(), s.getClubSessionId(),
                    Optional.ofNullable(s.getBlockSummaries()).map(List::size).orElse(0),
                    effectiveAlignment(s),
                    a != null ? a.getAthleteScore() : null,
                    a != null ? a.getCoachScore() : null,
                    a != null ? a.getCoachSource() : null);
        }
    }

    private static Integer effectiveAlignment(CompletedSession s) {
        return s.getAlignmentScore() != null ? s.getAlignmentScore().effectiveScore() : null;
    }
}
