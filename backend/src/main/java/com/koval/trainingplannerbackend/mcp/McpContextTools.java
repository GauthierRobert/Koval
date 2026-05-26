package com.koval.trainingplannerbackend.mcp;

import com.koval.trainingplannerbackend.auth.SecurityUtils;
import com.koval.trainingplannerbackend.auth.User;
import com.koval.trainingplannerbackend.auth.UserRole;
import com.koval.trainingplannerbackend.auth.UserService;
import com.koval.trainingplannerbackend.coach.CoachService;
import com.koval.trainingplannerbackend.coach.ScheduledWorkout;
import com.koval.trainingplannerbackend.coach.ScheduledWorkoutService;
import com.koval.trainingplannerbackend.config.Provenance;
import com.koval.trainingplannerbackend.config.exceptions.ForbiddenOperationException;
import com.koval.trainingplannerbackend.context.AthleteContext;
import com.koval.trainingplannerbackend.context.CoachContext;
import com.koval.trainingplannerbackend.context.ContextService;
import com.koval.trainingplannerbackend.context.ContextService.ContextEntry;
import com.koval.trainingplannerbackend.context.ContextService.MyContext;
import com.koval.trainingplannerbackend.goal.RaceGoalService;
import com.koval.trainingplannerbackend.plan.TrainingPlan;
import com.koval.trainingplannerbackend.plan.TrainingPlanService;
import com.koval.trainingplannerbackend.training.TrainingRepository;
import com.koval.trainingplannerbackend.training.history.AnalyticsService;
import com.koval.trainingplannerbackend.training.history.CompletedSessionRepository;
import com.koval.trainingplannerbackend.training.model.Training;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Consolidated context adapter. {@code getAthleteContext} replaces a chain of snapshot tools
 * (profile, current form, recent sessions, upcoming week, goals, active plan) plus the stored
 * coach/athlete context, returning everything an LLM needs to reason about an athlete in one
 * call. The two write tools persist context captured during onboarding.
 */
@Service
public class McpContextTools {

    private static final int RECENT_SESSION_DAYS = 14;
    private static final int SCHEDULE_LOOKAHEAD_DAYS = 7;

    private final UserService userService;
    private final RaceGoalService raceGoalService;
    private final CompletedSessionRepository sessionRepository;
    private final ScheduledWorkoutService scheduledWorkoutService;
    private final TrainingRepository trainingRepository;
    private final TrainingPlanService planService;
    private final ContextService contextService;
    private final CoachService coachService;
    private final AnalyticsService analyticsService;

    public McpContextTools(UserService userService,
                           RaceGoalService raceGoalService,
                           CompletedSessionRepository sessionRepository,
                           ScheduledWorkoutService scheduledWorkoutService,
                           TrainingRepository trainingRepository,
                           TrainingPlanService planService,
                           ContextService contextService,
                           CoachService coachService,
                           AnalyticsService analyticsService) {
        this.userService = userService;
        this.raceGoalService = raceGoalService;
        this.sessionRepository = sessionRepository;
        this.scheduledWorkoutService = scheduledWorkoutService;
        this.trainingRepository = trainingRepository;
        this.planService = planService;
        this.contextService = contextService;
        this.coachService = coachService;
        this.analyticsService = analyticsService;
    }

    @Tool(description = "Load everything you need to reason about an athlete in ONE call. Omit "
            + "athleteId to load your own context; pass an athleteId to load a coached athlete's "
            + "(requires COACH role and a coaching relationship). Returns identity + reference "
            + "values (FTP, weight, threshold pace, swim CSS), current training load (CTL=fitness, "
            + "ATL=fatigue, TSB=form), upcoming race goals, the last ~2 weeks of completed "
            + "sessions, the next 7 days of scheduled workouts, the active plan's current week, "
            + "and the stored context: the athlete's self-described preferences/habits, plus — "
            + "when you are the coach — your coaching philosophy and your private notes about this "
            + "athlete. Prefer this over calling profile/sessions/goals/schedule tools separately.")
    public AthleteContextPayload getAthleteContext(
            @ToolParam(description = "Coached athlete's user ID. Omit/null to load your own context.")
            String athleteId) {
        String callerId = SecurityUtils.getCurrentUserId();
        boolean coachView = athleteId != null && !athleteId.isBlank() && !athleteId.equals(callerId);

        String subjectId;
        if (coachView) {
            SecurityUtils.requireCoach();
            if (!coachService.isCoachOfAthlete(callerId, athleteId)) {
                throw new ForbiddenOperationException(
                        "Not authorized: you are not the coach of this athlete");
            }
            subjectId = athleteId;
        } else {
            subjectId = callerId;
        }

        User subject = userService.getUserById(subjectId);
        LocalDate today = LocalDate.now();

        List<McpHistoryTools.SessionSummary> recentSessions = sessionRepository
                .findByUserIdAndCompletedAtBetween(subjectId,
                        LocalDateTime.of(today.minusDays(RECENT_SESSION_DAYS), LocalTime.MIN),
                        LocalDateTime.of(today, LocalTime.MAX))
                .stream()
                .map(McpHistoryTools.SessionSummary::from)
                .toList();

        List<McpGoalTools.GoalSummary> goals = raceGoalService.getGoalsForAthlete(subjectId).stream()
                .map(McpGoalTools.GoalSummary::from)
                .toList();

        List<ScheduledWorkout> scheduled = scheduledWorkoutService.getAthleteSchedule(
                subjectId, today, today.plusDays(SCHEDULE_LOOKAHEAD_DAYS));
        Map<String, String> titles = batchResolveTitles(scheduled);
        List<McpSchedulingTools.ScheduleSummary> upcoming = scheduled.stream()
                .map(sw -> McpSchedulingTools.ScheduleSummary.from(
                        sw, titles.getOrDefault(sw.getTrainingId(), "Unknown")))
                .toList();

        ActivePlanInfo activePlan = findActivePlan(subjectId);

        Map<String, String> athleteContext = contextService.getAthleteSelfContext(subjectId)
                .map(AthleteContext::getSections).orElse(null);

        Map<String, String> coachAboutAthlete = null;
        Map<String, String> coachPhilosophy = null;
        if (coachView) {
            // The calling coach's own philosophy + their private context about this athlete.
            coachPhilosophy = contextService.getCoachPhilosophy(callerId)
                    .map(CoachContext::getSections).orElse(null);
            ContextEntry coachEntry = contextService.getCoachViewOfAthlete(callerId, athleteId).coachContext();
            coachAboutAthlete = coachEntry != null ? coachEntry.sections() : null;
        } else if (subject.getRole() == UserRole.COACH) {
            coachPhilosophy = contextService.getCoachPhilosophy(subjectId)
                    .map(CoachContext::getSections).orElse(null);
        }

        return new AthleteContextPayload(
                Subject.from(subject),
                currentTrainingLoad(subjectId, today, subject),
                goals,
                recentSessions,
                upcoming,
                activePlan,
                athleteContext,
                coachAboutAthlete,
                coachPhilosophy);
    }

    @Tool(description = "Save YOUR own context as structured sections (section title → markdown). "
            + "Athletes: persist your self-context (availability, habits, how you want to be "
            + "trained, current status). Coaches: persist your coaching philosophy (how you train "
            + "athletes). Replaces your previously stored context. Typically called at the end of "
            + "onboarding. The matching section titles from the onboarding template are recommended.")
    public MyContext updateMyContext(
            @ToolParam(description = "Section title → markdown content. E.g. {\"Weekly availability\": "
                    + "\"8-10h/week, rest Mondays\", \"Voice\": \"terse, French\"}.")
            Map<String, String> sections) {
        return contextService.upsertMyContext(
                SecurityUtils.getCurrentUserId(), sections, Provenance.mcp());
    }

    @Tool(description = "Save your PRIVATE coaching context about a specific athlete you manage "
            + "(section title → markdown). This is visible only to you (the coach), never to the "
            + "athlete, and is folded into getAthleteContext when you load that athlete. Use it to "
            + "record how you intend to coach this individual. Requires a coaching relationship.")
    public ContextEntry setAthleteCoachingContext(
            @ToolParam(description = "Coached athlete's user ID") String athleteId,
            @ToolParam(description = "Section title → markdown content describing your plan for this athlete")
            Map<String, String> sections) {
        SecurityUtils.requireCoach();
        AthleteContext saved = contextService.upsertCoachAthleteContext(
                SecurityUtils.getCurrentUserId(), athleteId, sections, Provenance.mcp());
        return ContextEntry.from(saved);
    }

    /**
     * Compute CTL/ATL/TSB live as of today (with rest-day decay) rather than reading the
     * cached values on the User document. The cached fields are only refreshed when a session
     * is logged/imported or a workout is scheduled, so they go stale on rest days — this keeps
     * the context tool consistent with the live PMC tools ({@code getPmcData}/{@code getAthletePmc}).
     */
    private TrainingLoad currentTrainingLoad(String subjectId, LocalDate today, User subject) {
        List<AnalyticsService.PmcDataPoint> pmc = analyticsService.generatePmc(subjectId, today, today);
        if (pmc.isEmpty()) {
            return new TrainingLoad(subject.getCtl(), subject.getAtl(), subject.getTsb());
        }
        AnalyticsService.PmcDataPoint point = pmc.getLast();
        return new TrainingLoad(point.ctl(), point.atl(), point.tsb());
    }

    private ActivePlanInfo findActivePlan(String userId) {
        return planService.listPlans(userId).stream()
                .filter(p -> p.getStatus() != null && "ACTIVE".equals(p.getStatus().name()))
                .findFirst()
                .map(p -> new ActivePlanInfo(
                        p.getId(), p.getTitle(),
                        p.getStatus().name(),
                        computeCurrentWeek(p.getStartDate()),
                        p.getDurationWeeks()))
                .orElse(null);
    }

    private static int computeCurrentWeek(LocalDate startDate) {
        if (startDate == null) return 0;
        long daysSinceStart = LocalDate.now().toEpochDay() - startDate.toEpochDay();
        if (daysSinceStart < 0) return 0;
        return (int) (daysSinceStart / 7) + 1;
    }

    private Map<String, String> batchResolveTitles(List<ScheduledWorkout> workouts) {
        Set<String> trainingIds = workouts.stream()
                .map(ScheduledWorkout::getTrainingId)
                .filter(id -> id != null && !id.isBlank())
                .collect(Collectors.toSet());
        if (trainingIds.isEmpty()) return Map.of();
        Map<String, String> titles = new HashMap<>(trainingIds.size());
        for (Training t : trainingRepository.findAllById(new ArrayList<>(trainingIds))) {
            titles.put(t.getId(), t.getTitle());
        }
        return titles;
    }

    public record AthleteContextPayload(
            Subject subject,
            TrainingLoad trainingLoad,
            List<McpGoalTools.GoalSummary> goals,
            List<McpHistoryTools.SessionSummary> recentSessions,
            List<McpSchedulingTools.ScheduleSummary> upcomingSchedule,
            ActivePlanInfo activePlan,
            Map<String, String> athleteContext,
            Map<String, String> coachContextAboutAthlete,
            Map<String, String> coachPhilosophy) {}

    public record Subject(String id, String displayName, String role, Integer ftp, Integer weightKg,
                          Integer functionalThresholdPace, Integer criticalSwimSpeed,
                          Integer pace5k, Integer pace10k) {
        static Subject from(User u) {
            return new Subject(u.getId(), u.getDisplayName(),
                    u.getRole() != null ? u.getRole().name() : null,
                    u.getFtp(), u.getWeightKg(),
                    u.getFunctionalThresholdPace(), u.getCriticalSwimSpeed(),
                    u.getPace5k(), u.getPace10k());
        }
    }

    public record TrainingLoad(Double ctl, Double atl, Double tsb) {}

    public record ActivePlanInfo(String planId, String title, String status, int currentWeek,
                                 int durationWeeks) {}
}
