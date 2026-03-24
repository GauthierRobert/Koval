package com.koval.trainingplannerbackend.plan;

import com.koval.trainingplannerbackend.ai.ToolEventEmitter;
import com.koval.trainingplannerbackend.training.model.SportType;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Service;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * AI-facing tool service for Training Plan (periodization) operations.
 * Returns lean summaries to minimize token usage.
 */
@Service
public class PlanToolService {

    private final TrainingPlanService planService;

    public PlanToolService(TrainingPlanService planService) {
        this.planService = planService;
    }

    @Tool(description = "Create a new multi-week training plan (periodization). Returns the plan with its ID. The plan starts in DRAFT status — call activatePlan to populate the calendar.")
    public Object createPlan(
            @ToolParam(description = "Plan title, e.g. '8-Week FTP Builder'") String title,
            @ToolParam(description = "Plan description with periodization rationale") String description,
            @ToolParam(description = "Sport type: CYCLING, RUNNING, SWIMMING, or BRICK") String sportType,
            @ToolParam(description = "Start date in YYYY-MM-DD format (should be a Monday)") LocalDate startDate,
            @ToolParam(description = "Total number of weeks in the plan") int durationWeeks,
            @ToolParam(description = "User ID of the plan creator") String userId,
            @ToolParam(description = "Optional target FTP at plan completion", required = false) Integer targetFtp,
            @ToolParam(description = "Optional race goal ID this plan targets", required = false) String goalRaceId,
            ToolContext context) {

        if (title == null || title.isBlank()) {
            ToolEventEmitter.emitToolResult(context, "createPlan", "Validation failed", false);
            return "Error: title is required.";
        }
        if (durationWeeks < 1 || durationWeeks > 52) {
            ToolEventEmitter.emitToolResult(context, "createPlan", "Validation failed", false);
            return "Error: durationWeeks must be between 1 and 52.";
        }

        ToolEventEmitter.emitToolCall(context, "createPlan", "Creating plan: " + title);

        TrainingPlan plan = new TrainingPlan();
        plan.setTitle(title);
        plan.setDescription(description);
        plan.setSportType(SportType.fromString(sportType));
        plan.setStartDate(startDate);
        plan.setDurationWeeks(durationWeeks);
        plan.setTargetFtp(targetFtp);
        plan.setGoalRaceId(goalRaceId);

        // Initialize empty weeks
        List<PlanWeek> weeks = new ArrayList<>();
        for (int i = 1; i <= durationWeeks; i++) {
            PlanWeek week = new PlanWeek();
            week.setWeekNumber(i);
            weeks.add(week);
        }
        plan.setWeeks(weeks);

        TrainingPlan created = planService.createPlan(plan, userId);
        ToolEventEmitter.emitToolResult(context, "createPlan", created.getTitle(), true);
        return PlanSummary.from(created);
    }

    @Tool(description = "Add a workout to a specific day in a specific week of a training plan. Call this after creating both the plan and the training workout.")
    public Object addDayToPlan(
            @ToolParam(description = "The plan ID") String planId,
            @ToolParam(description = "Week number (1-based)") int weekNumber,
            @ToolParam(description = "Day of week: MONDAY, TUESDAY, WEDNESDAY, THURSDAY, FRIDAY, SATURDAY, or SUNDAY") String dayOfWeek,
            @ToolParam(description = "Training ID to schedule on this day") String trainingId,
            @ToolParam(description = "Optional notes for this day", required = false) String notes,
            @ToolParam(description = "User ID for ownership check") String userId,
            ToolContext context) {

        if (planId == null || planId.isBlank()) {
            ToolEventEmitter.emitToolResult(context, "addDayToPlan", "Validation failed", false);
            return "Error: planId is required.";
        }

        ToolEventEmitter.emitToolCall(context, "addDayToPlan", "Adding workout to week " + weekNumber);

        TrainingPlan plan = planService.getPlan(planId);
        PlanWeek week = plan.getWeeks().stream()
                .filter(w -> w.getWeekNumber() == weekNumber)
                .findFirst()
                .orElse(null);

        if (week == null) {
            ToolEventEmitter.emitToolResult(context, "addDayToPlan", "Week not found", false);
            return "Error: week " + weekNumber + " not found in plan (plan has " + plan.getDurationWeeks() + " weeks).";
        }

        PlanDay day = new PlanDay();
        day.setDayOfWeek(DayOfWeek.valueOf(dayOfWeek.toUpperCase()));
        day.setTrainingId(trainingId);
        day.setNotes(notes);
        week.getDays().add(day);

        planService.updatePlan(planId, plan, userId);
        ToolEventEmitter.emitToolResult(context, "addDayToPlan", "Added to week " + weekNumber + " " + dayOfWeek, true);
        return "Added training to week " + weekNumber + ", " + dayOfWeek;
    }

    @Tool(description = "Set the label and optional target TSS for a week in the plan. Use to annotate periodization phases (e.g. 'Base Phase', 'Build', 'Recovery').")
    public Object setWeekLabel(
            @ToolParam(description = "The plan ID") String planId,
            @ToolParam(description = "Week number (1-based)") int weekNumber,
            @ToolParam(description = "Label for the week, e.g. 'Build Week 1', 'Recovery'") String label,
            @ToolParam(description = "Target weekly TSS", required = false) Integer targetTss,
            @ToolParam(description = "User ID for ownership check") String userId,
            ToolContext context) {

        ToolEventEmitter.emitToolCall(context, "setWeekLabel", "Labeling week " + weekNumber);

        TrainingPlan plan = planService.getPlan(planId);
        PlanWeek week = plan.getWeeks().stream()
                .filter(w -> w.getWeekNumber() == weekNumber)
                .findFirst()
                .orElse(null);

        if (week == null) {
            ToolEventEmitter.emitToolResult(context, "setWeekLabel", "Week not found", false);
            return "Error: week " + weekNumber + " not found.";
        }

        week.setLabel(label);
        if (targetTss != null) week.setTargetTss(targetTss);

        planService.updatePlan(planId, plan, userId);
        ToolEventEmitter.emitToolResult(context, "setWeekLabel", label, true);
        return "Week " + weekNumber + " labeled: " + label;
    }

    @Tool(description = "Activate a training plan, populating the calendar with scheduled workouts for all planned days. Only works on DRAFT or PAUSED plans.")
    public Object activatePlan(
            @ToolParam(description = "The plan ID to activate") String planId,
            @ToolParam(description = "User ID for ownership check") String userId,
            ToolContext context) {

        ToolEventEmitter.emitToolCall(context, "activatePlan", "Activating plan...");

        try {
            TrainingPlan activated = planService.activatePlan(planId, userId);
            int totalDays = activated.getWeeks().stream()
                    .mapToInt(w -> w.getDays().size())
                    .sum();
            ToolEventEmitter.emitToolResult(context, "activatePlan",
                    "Activated: " + totalDays + " workouts scheduled", true);
            return "Plan activated! " + totalDays + " workouts have been added to the calendar starting "
                    + activated.getStartDate();
        } catch (Exception e) {
            ToolEventEmitter.emitToolResult(context, "activatePlan", e.getMessage(), false);
            return "Error activating plan: " + e.getMessage();
        }
    }

    @Tool(description = "List training plans for a user. Returns summaries with status and duration.")
    public List<PlanSummary> listPlans(
            @ToolParam(description = "User ID") String userId) {
        return planService.listPlans(userId).stream()
                .map(PlanSummary::from)
                .toList();
    }

    @Tool(description = "Get the progress of a training plan — how many workouts are completed, skipped, or pending.")
    public Object getPlanProgress(
            @ToolParam(description = "The plan ID") String planId,
            ToolContext context) {
        ToolEventEmitter.emitToolCall(context, "getPlanProgress", "Checking progress...");
        PlanProgress progress = planService.getProgress(planId);
        ToolEventEmitter.emitToolResult(context, "getPlanProgress",
                progress.completionPercent() + "% complete", true);
        return progress;
    }

    // ── Summary record ──────────────────────────────────────────────────

    public record PlanSummary(
            String id,
            String title,
            String sportType,
            String status,
            int durationWeeks,
            String startDate,
            int totalWorkouts
    ) {
        public static PlanSummary from(TrainingPlan plan) {
            int totalWorkouts = plan.getWeeks().stream()
                    .mapToInt(w -> w.getDays().size())
                    .sum();
            return new PlanSummary(
                    plan.getId(),
                    plan.getTitle(),
                    plan.getSportType() != null ? plan.getSportType().name() : null,
                    plan.getStatus().name(),
                    plan.getDurationWeeks(),
                    plan.getStartDate() != null ? plan.getStartDate().toString() : null,
                    totalWorkouts);
        }
    }
}
