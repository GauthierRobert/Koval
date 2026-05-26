package com.koval.trainingplannerbackend.mcp;

import com.koval.trainingplannerbackend.auth.SecurityUtils;
import com.koval.trainingplannerbackend.plan.PlanAnalytics;
import com.koval.trainingplannerbackend.plan.PlanAnalyticsService;
import com.koval.trainingplannerbackend.plan.PlanDay;
import com.koval.trainingplannerbackend.plan.PlanWeek;
import com.koval.trainingplannerbackend.plan.TrainingPlan;
import com.koval.trainingplannerbackend.plan.TrainingPlanService;
import com.koval.trainingplannerbackend.training.model.SportType;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Service;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * MCP tool adapter for multi-week training plan (periodization) management.
 */
@Service
public class McpPlanTools {

    private final TrainingPlanService planService;
    private final PlanAnalyticsService analyticsService;

    public McpPlanTools(TrainingPlanService planService,
                        PlanAnalyticsService analyticsService) {
        this.planService = planService;
        this.analyticsService = analyticsService;
    }

    @Tool(description = "List all training plans for the user. Training plans are multi-week periodized programs with workouts assigned to specific days. Plans can be DRAFT, ACTIVE, PAUSED, COMPLETED, or CANCELLED.")
    public List<PlanSummary> listPlans() {
        String userId = SecurityUtils.getCurrentUserId();
        return planService.listPlans(userId).stream()
                .map(PlanSummary::from)
                .toList();
    }

    @Tool(description = "Create a new multi-week training plan. The plan starts in DRAFT status with empty weeks. Use addDayToPlan to assign workouts to specific days, then activatePlan to populate the calendar.")
    public Object createPlan(
            @ToolParam(description = "Plan title (e.g. '8-Week FTP Builder')") String title,
            @ToolParam(description = "Description with periodization rationale") String description,
            @ToolParam(description = "Sport: CYCLING, RUNNING, SWIMMING, or BRICK") String sportType,
            @ToolParam(description = "Start date (YYYY-MM-DD, should be a Monday)") LocalDate startDate,
            @ToolParam(description = "Total weeks in plan (1-52)") int durationWeeks,
            @ToolParam(description = "Optional target FTP at plan completion") Integer targetFtp,
            @ToolParam(description = "Optional race goal ID this plan targets") String goalRaceId) {
        if (title == null || title.isBlank()) return "Error: title is required.";
        if (durationWeeks < 1 || durationWeeks > 52) return "Error: durationWeeks must be 1-52.";

        String userId = SecurityUtils.getCurrentUserId();
        TrainingPlan plan = new TrainingPlan();
        plan.setTitle(title);
        plan.setDescription(description);
        plan.setSportType(SportType.fromString(sportType));
        plan.setStartDate(startDate);
        plan.setDurationWeeks(durationWeeks);
        plan.setTargetFtp(targetFtp);
        plan.setGoalRaceId(goalRaceId);

        List<PlanWeek> weeks = IntStream.rangeClosed(1, durationWeeks)
                .mapToObj(i -> {
                    PlanWeek week = new PlanWeek();
                    week.setWeekNumber(i);
                    return week;
                })
                .collect(Collectors.toCollection(ArrayList::new));
        plan.setWeeks(weeks);

        return PlanSummary.from(planService.createPlan(plan, userId));
    }

    @Tool(description = "Add a training workout to a specific day in a specific week of a plan. The training must exist first. Multiple workouts can be assigned to the same day (e.g. AM swim + PM bike for triathletes) — calling this repeatedly for the same day appends additional workouts.")
    public Object addDayToPlan(
            @ToolParam(description = "Plan ID") String planId,
            @ToolParam(description = "Week number (1-based)") int weekNumber,
            @ToolParam(description = "Day: MONDAY, TUESDAY, WEDNESDAY, THURSDAY, FRIDAY, SATURDAY, SUNDAY") String dayOfWeek,
            @ToolParam(description = "Training ID to assign") String trainingId,
            @ToolParam(description = "Optional notes for this day. When appending to a day that already has notes, the new value replaces the existing notes if provided.") String notes) {
        if (planId == null || planId.isBlank()) return "Error: planId is required.";
        if (trainingId == null || trainingId.isBlank()) return "Error: trainingId is required.";

        TrainingPlan plan = planService.getPlan(planId);
        PlanWeek week = plan.getWeeks().stream()
                .filter(w -> w.getWeekNumber() == weekNumber)
                .findFirst().orElse(null);
        if (week == null) return "Error: week " + weekNumber + " not found.";

        DayOfWeek dow = DayOfWeek.valueOf(dayOfWeek.toUpperCase());
        PlanDay day = week.getDays().stream()
                .filter(d -> d.getDayOfWeek() == dow)
                .findFirst()
                .orElseGet(() -> {
                    PlanDay nd = new PlanDay();
                    nd.setDayOfWeek(dow);
                    week.getDays().add(nd);
                    return nd;
                });
        if (!day.getTrainingIds().contains(trainingId)) {
            day.getTrainingIds().add(trainingId);
        }
        if (notes != null && !notes.isBlank()) day.setNotes(notes);

        String userId = SecurityUtils.getCurrentUserId();
        planService.updatePlan(planId, plan, userId);
        return "Added training to week " + weekNumber + ", " + dayOfWeek
                + " (" + day.getTrainingIds().size() + " workout(s) now scheduled).";
    }

    @Tool(description = "Change a training plan's lifecycle status. status='ACTIVE' with a startDate (YYYY-MM-DD, ideally a Monday) materializes the plan onto the calendar, creating scheduled workouts for every planned day — works on DRAFT or PAUSED plans. status='ACTIVE' without a startDate resumes a PAUSED plan in place without recreating workouts. status='PAUSED' cancels future pending scheduled workouts but keeps past completed/skipped ones. Returns the updated plan summary.")
    public PlanSummary setPlanStatus(
            @ToolParam(description = "Plan ID") String planId,
            @ToolParam(description = "Target status: 'ACTIVE' or 'PAUSED'.") String status,
            @ToolParam(description = "Start date (YYYY-MM-DD, should be a Monday). Provide to activate/reschedule; omit (null) to resume a paused plan in place.") LocalDate startDate) {
        if (planId == null || planId.isBlank()) throw new IllegalArgumentException("planId is required.");
        if (status == null || status.isBlank()) throw new IllegalArgumentException("status is required ('ACTIVE' or 'PAUSED').");
        String userId = SecurityUtils.getCurrentUserId();
        return switch (status.trim().toUpperCase()) {
            case "ACTIVE" -> startDate != null
                    ? PlanSummary.from(planService.activatePlan(planId, userId, startDate))
                    : PlanSummary.from(planService.resumePlan(planId, userId));
            case "PAUSED" -> PlanSummary.from(planService.pausePlan(planId, userId));
            default -> throw new IllegalArgumentException("status must be 'ACTIVE' or 'PAUSED'.");
        };
    }

    @Tool(description = "Get the full structure of a training plan: title, description, sport, status, start date, weeks with their target TSS labels, and the planned workouts per day (training id, day of week, notes).")
    public PlanDetail getPlan(
            @ToolParam(description = "Plan ID") String planId) {
        return PlanDetail.from(planService.getPlan(planId));
    }

    @Tool(description = "Update a training plan's metadata. Only the fields you pass (non-null) are updated; pass null to leave a field unchanged. Cannot be used to mutate the per-week structure — use addDayToPlan / removeDayFromPlan for that.")
    public PlanSummary updatePlan(
            @ToolParam(description = "Plan ID") String planId,
            @ToolParam(description = "New title (null = unchanged)") String title,
            @ToolParam(description = "New description (null = unchanged)") String description,
            @ToolParam(description = "New target FTP at plan completion (null = unchanged)") Integer targetFtp,
            @ToolParam(description = "New goal race ID (null = unchanged)") String goalRaceId) {
        String userId = SecurityUtils.getCurrentUserId();
        TrainingPlan updates = new TrainingPlan();
        updates.setTitle(title);
        updates.setDescription(description);
        updates.setTargetFtp(targetFtp);
        updates.setGoalRaceId(goalRaceId);
        return PlanSummary.from(planService.updatePlan(planId, updates, userId));
    }

    @Tool(description = "Remove planned workouts from a specific day of a week. If trainingId is provided, removes only that workout from the day (leaving any others). If trainingId is null/blank, removes the entire day with all its workouts.")
    public Object removeDayFromPlan(
            @ToolParam(description = "Plan ID") String planId,
            @ToolParam(description = "Week number (1-based)") int weekNumber,
            @ToolParam(description = "Day: MONDAY, TUESDAY, WEDNESDAY, THURSDAY, FRIDAY, SATURDAY, SUNDAY") String dayOfWeek,
            @ToolParam(description = "Optional training ID — when provided, removes only this workout from the day; when null, clears the entire day.") String trainingId) {
        if (planId == null || planId.isBlank()) return "Error: planId is required.";
        TrainingPlan plan = planService.getPlan(planId);
        PlanWeek week = plan.getWeeks().stream()
                .filter(w -> w.getWeekNumber() == weekNumber)
                .findFirst().orElse(null);
        if (week == null) return "Error: week " + weekNumber + " not found.";

        DayOfWeek dow = DayOfWeek.valueOf(dayOfWeek.toUpperCase());

        if (trainingId == null || trainingId.isBlank()) {
            boolean removed = week.getDays().removeIf(d -> d.getDayOfWeek() == dow);
            if (!removed) return "Error: no planned day found for " + dayOfWeek + " in week " + weekNumber + ".";
        } else {
            PlanDay day = week.getDays().stream()
                    .filter(d -> d.getDayOfWeek() == dow)
                    .findFirst().orElse(null);
            if (day == null) return "Error: no planned day found for " + dayOfWeek + " in week " + weekNumber + ".";
            if (!day.getTrainingIds().remove(trainingId)) {
                return "Error: training " + trainingId + " is not scheduled on " + dayOfWeek + " of week " + weekNumber + ".";
            }
            if (day.getTrainingIds().isEmpty()) {
                week.getDays().remove(day);
            }
        }

        String userId = SecurityUtils.getCurrentUserId();
        TrainingPlan updates = new TrainingPlan();
        updates.setWeeks(plan.getWeeks());
        planService.updatePlan(planId, updates, userId);
        return "Removed from " + dayOfWeek + " of week " + weekNumber + ".";
    }

    public record PlanDetail(String id, String title, String description, String sport,
                              String status, String startDate, int durationWeeks,
                              Integer targetFtp, String goalRaceId, List<WeekDetail> weeks) {
        public static PlanDetail from(TrainingPlan p) {
            List<WeekDetail> weekDetails = p.getWeeks().stream()
                    .map(w -> new WeekDetail(w.getWeekNumber(), w.getLabel(), w.getTargetTss(),
                            w.getDays().stream()
                                    .map(d -> new DayDetail(
                                            Optional.ofNullable(d.getDayOfWeek()).map(DayOfWeek::name).orElse(null),
                                            List.copyOf(d.getTrainingIds()),
                                            d.getNotes(),
                                            List.copyOf(d.getScheduledWorkoutIds())))
                                    .toList()))
                    .toList();
            return new PlanDetail(
                    p.getId(), p.getTitle(), p.getDescription(),
                    Optional.ofNullable(p.getSportType()).map(Enum::name).orElse(null),
                    Optional.ofNullable(p.getStatus()).map(Enum::name).orElse(null),
                    Optional.ofNullable(p.getStartDate()).map(LocalDate::toString).orElse(null),
                    p.getDurationWeeks(), p.getTargetFtp(), p.getGoalRaceId(), weekDetails);
        }
    }

    public record WeekDetail(int weekNumber, String label, Integer targetTss, List<DayDetail> days) {}

    public record DayDetail(String dayOfWeek, List<String> trainingIds, String notes, List<String> scheduledWorkoutIds) {}

    public record PlanSummary(String id, String title, String sport, String status,
                               int durationWeeks, String startDate, int totalWorkouts) {
        public static PlanSummary from(TrainingPlan p) {
            int total = p.getWeeks().stream()
                    .flatMap(w -> w.getDays().stream())
                    .mapToInt(d -> d.getTrainingIds().size())
                    .sum();
            return new PlanSummary(
                    p.getId(), p.getTitle(),
                    p.getSportType() != null ? p.getSportType().name() : null,
                    p.getStatus().name(), p.getDurationWeeks(),
                    p.getStartDate() != null ? p.getStartDate().toString() : null,
                    total);
        }
    }
}
