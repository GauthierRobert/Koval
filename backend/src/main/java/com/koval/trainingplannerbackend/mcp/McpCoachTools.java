package com.koval.trainingplannerbackend.mcp;

import com.koval.trainingplannerbackend.auth.SecurityUtils;
import com.koval.trainingplannerbackend.auth.User;
import com.koval.trainingplannerbackend.auth.UserService;
import com.koval.trainingplannerbackend.coach.CoachService;
import com.koval.trainingplannerbackend.coach.ScheduledWorkout;
import com.koval.trainingplannerbackend.coach.ScheduledWorkoutService;
import com.koval.trainingplannerbackend.coach.dto.AthleteResponse;
import com.koval.trainingplannerbackend.training.TrainingService;
import com.koval.trainingplannerbackend.training.history.AnalyticsService;
import com.koval.trainingplannerbackend.training.history.AnalyticsService.PmcDataPoint;
import com.koval.trainingplannerbackend.training.history.CompletedSession;
import com.koval.trainingplannerbackend.training.history.CompletedSessionRepository;
import com.koval.trainingplannerbackend.training.metrics.PowerCurveService;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * MCP tool adapter for coach operations.
 * Only usable by users with COACH role.
 */
@Service
public class McpCoachTools {

    private final CoachService coachService;
    private final ScheduledWorkoutService scheduledWorkoutService;
    private final TrainingService trainingService;
    private final UserService userService;
    private final CompletedSessionRepository sessionRepository;
    private final AnalyticsService analyticsService;
    private final PowerCurveService powerCurveService;

    public McpCoachTools(CoachService coachService,
                         ScheduledWorkoutService scheduledWorkoutService,
                         TrainingService trainingService,
                         UserService userService,
                         CompletedSessionRepository sessionRepository,
                         AnalyticsService analyticsService,
                         PowerCurveService powerCurveService) {
        this.coachService = coachService;
        this.scheduledWorkoutService = scheduledWorkoutService;
        this.trainingService = trainingService;
        this.userService = userService;
        this.sessionRepository = sessionRepository;
        this.analyticsService = analyticsService;
        this.powerCurveService = powerCurveService;
    }

    @Tool(description = "List all athletes coached by the current user. Returns athlete profiles with FTP, weight, and performance metrics. Requires COACH role.")
    public List<AthleteResponse> listAthletes() {
        String coachId = SecurityUtils.getCurrentUserId();
        return coachService.getAthletes(coachId);
    }

    @Tool(description = "Assign a training workout to one or more athletes on a specific date. Requires COACH role.")
    public Object assignTraining(
            @ToolParam(description = "Training ID to assign") String trainingId,
            @ToolParam(description = "List of athlete IDs to assign to") List<String> athleteIds,
            @ToolParam(description = "Date to schedule (YYYY-MM-DD)") LocalDate scheduledDate,
            @ToolParam(description = "Optional notes for the athletes") String notes) {
        if (trainingId == null || trainingId.isBlank()) return "Error: trainingId is required.";
        if (athleteIds == null || athleteIds.isEmpty()) return "Error: athleteIds list is required.";
        if (scheduledDate == null) return "Error: scheduledDate is required.";

        String coachId = SecurityUtils.getCurrentUserId();
        List<ScheduledWorkout> workouts = coachService.assignTraining(
                coachId, trainingId, athleteIds, scheduledDate, notes, null);
        String title = trainingService.getTrainingById(trainingId).getTitle();
        return workouts.stream()
                .map(sw -> McpSchedulingTools.ScheduleSummary.from(sw, title))
                .toList();
    }

    @Tool(description = "Get a specific athlete's scheduled workouts within a date range. Requires COACH role.")
    public List<McpSchedulingTools.ScheduleSummary> getAthleteSchedule(
            @ToolParam(description = "Athlete ID") String athleteId,
            @ToolParam(description = "Start date inclusive (YYYY-MM-DD)") LocalDate from,
            @ToolParam(description = "End date inclusive (YYYY-MM-DD)") LocalDate to) {
        return scheduledWorkoutService.getAthleteSchedule(athleteId, from, to).stream()
                .map(sw -> {
                    String title = resolveTitle(sw.getTrainingId());
                    return McpSchedulingTools.ScheduleSummary.from(sw, title);
                }).toList();
    }

    @Tool(description = "Get a coached athlete's profile: name, FTP, weight, threshold pace, swim CSS, and current training load (CTL/ATL/TSB). Requires the current user to be the athlete's coach.")
    public AthleteProfile getAthleteProfile(
            @ToolParam(description = "Athlete user ID") String athleteId) {
        String coachId = SecurityUtils.getCurrentUserId();
        verifyCoach(coachId, athleteId);
        User u = userService.getUserById(athleteId);
        return AthleteProfile.from(u);
    }

    @Tool(description = "Get a coached athlete's most recent completed sessions. Requires COACH relationship to the athlete.")
    public List<McpHistoryTools.SessionSummary> getAthleteRecentSessions(
            @ToolParam(description = "Athlete user ID") String athleteId,
            @ToolParam(description = "Maximum number of sessions to return (default 10, max 50)") Integer limit) {
        String coachId = SecurityUtils.getCurrentUserId();
        verifyCoach(coachId, athleteId);
        int effectiveLimit = (limit != null && limit > 0) ? Math.min(limit, 50) : 10;
        return sessionRepository.findByUserIdOrderByCompletedAtDesc(athleteId).stream()
                .limit(effectiveLimit)
                .map(McpHistoryTools.SessionSummary::from)
                .toList();
    }

    @Tool(description = "Get a coached athlete's PMC (Performance Management Chart) data — daily CTL/ATL/TSB — over a date range. Requires COACH relationship.")
    public List<PmcDataPoint> getAthletePmc(
            @ToolParam(description = "Athlete user ID") String athleteId,
            @ToolParam(description = "Start date inclusive (YYYY-MM-DD)") LocalDate from,
            @ToolParam(description = "End date inclusive (YYYY-MM-DD)") LocalDate to) {
        String coachId = SecurityUtils.getCurrentUserId();
        verifyCoach(coachId, athleteId);
        return analyticsService.generatePmc(athleteId, from, to);
    }

    @Tool(description = "Get a coached athlete's best mean-maximal power curve over a date range (cycling sessions only). Requires COACH relationship.")
    public Map<Integer, Double> getAthletePowerCurve(
            @ToolParam(description = "Athlete user ID") String athleteId,
            @ToolParam(description = "Start date inclusive (YYYY-MM-DD)") LocalDate from,
            @ToolParam(description = "End date inclusive (YYYY-MM-DD)") LocalDate to) {
        String coachId = SecurityUtils.getCurrentUserId();
        verifyCoach(coachId, athleteId);
        return powerCurveService.getBestPowerCurve(athleteId, from, to);
    }

    private void verifyCoach(String coachId, String athleteId) {
        if (!coachService.isCoachOfAthlete(coachId, athleteId)) {
            throw new IllegalStateException("Not authorized: you are not the coach of this athlete.");
        }
    }

    public record AthleteProfile(String id, String displayName, Integer ftp, Integer weightKg,
                                  Integer functionalThresholdPace, Integer criticalSwimSpeed,
                                  Double ctl, Double atl, Double tsb) {
        public static AthleteProfile from(User u) {
            return new AthleteProfile(
                    u.getId(), u.getDisplayName(),
                    u.getFtp(), u.getWeightKg(),
                    u.getFunctionalThresholdPace(), u.getCriticalSwimSpeed(),
                    u.getCtl(), u.getAtl(), u.getTsb());
        }
    }

    private String resolveTitle(String trainingId) {
        if (trainingId == null) return "Unknown";
        try {
            return trainingService.getTrainingById(trainingId).getTitle();
        } catch (Exception e) {
            return "Unknown";
        }
    }
}
