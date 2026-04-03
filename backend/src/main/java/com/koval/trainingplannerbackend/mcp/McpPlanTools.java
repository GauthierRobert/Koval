package com.koval.trainingplannerbackend.mcp;

import com.koval.trainingplannerbackend.auth.SecurityUtils;
import com.koval.trainingplannerbackend.plan.PlanAnalytics;
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

/**
 * MCP tool adapter for multi-week training plan (periodization) management.
 */
@Service
public class McpPlanTools {

    private final TrainingPlanService planService;

    public McpPlanTools(TrainingPlanService planService) {
        this.planService = planService;
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

        List<PlanWeek> weeks = new ArrayList<>();
        for (int i = 1; i <= durationWeeks; i++) {
            PlanWeek week = new PlanWeek();
            week.setWeekNumber(i);
            weeks.add(week);
        }
        plan.setWeeks(weeks);

        return PlanSummary.from(planService.createPlan(plan, userId));
    }

    @Tool(description = "Add a training workout to a specific day in a specific week of a plan. The training must exist first.")
    public Object addDayToPlan(
            @ToolParam(description = "Plan ID") String planId,
            @ToolParam(description = "Week number (1-based)") int weekNumber,
            @ToolParam(description = "Day: MONDAY, TUESDAY, WEDNESDAY, THURSDAY, FRIDAY, SATURDAY, SUNDAY") String dayOfWeek,
            @ToolParam(description = "Training ID to assign") String trainingId,
            @ToolParam(description = "Optional notes for this day") String notes) {
        if (planId == null || planId.isBlank()) return "Error: planId is required.";

        TrainingPlan plan = planService.getPlan(planId);
        PlanWeek week = plan.getWeeks().stream()
                .filter(w -> w.getWeekNumber() == weekNumber)
                .findFirst().orElse(null);
        if (week == null) return "Error: week " + weekNumber + " not found.";

        PlanDay day = new PlanDay();
        day.setDayOfWeek(DayOfWeek.valueOf(dayOfWeek.toUpperCase()));
        day.setTrainingId(trainingId);
        day.setNotes(notes);
        week.getDays().add(day);

        String userId = SecurityUtils.getCurrentUserId();
        planService.updatePlan(planId, plan, userId);
        return "Added training to week " + weekNumber + ", " + dayOfWeek;
    }

    @Tool(description = "Activate a training plan, creating scheduled workouts in the calendar for all planned days. Only works on DRAFT or PAUSED plans. Requires a start date.")
    public Object activatePlan(
            @ToolParam(description = "Plan ID to activate") String planId,
            @ToolParam(description = "Start date in YYYY-MM-DD format (should be a Monday)") LocalDate startDate) {
        String userId = SecurityUtils.getCurrentUserId();
        TrainingPlan activated = planService.activatePlan(planId, userId, startDate);
        int totalDays = activated.getWeeks().stream().mapToInt(w -> w.getDays().size()).sum();
        return "Plan activated! " + totalDays + " workouts scheduled starting " + activated.getStartDate();
    }

    @Tool(description = "Get progress of a training plan: how many workouts are completed, skipped, or pending.")
    public Object getPlanProgress(
            @ToolParam(description = "Plan ID") String planId) {
        return planService.getProgress(planId);
    }

    @Tool(description = "Get detailed analytics for a training plan including weekly TSS adherence (actual vs target), completion rates per week, and overall adherence percentage.")
    public PlanAnalytics getPlanAnalytics(
            @ToolParam(description = "Plan ID") String planId) {
        return planService.getAnalytics(planId);
    }

    public record PlanSummary(String id, String title, String sport, String status,
                               int durationWeeks, String startDate, int totalWorkouts) {
        public static PlanSummary from(TrainingPlan p) {
            int total = p.getWeeks().stream().mapToInt(w -> w.getDays().size()).sum();
            return new PlanSummary(
                    p.getId(), p.getTitle(),
                    p.getSportType() != null ? p.getSportType().name() : null,
                    p.getStatus().name(), p.getDurationWeeks(),
                    p.getStartDate() != null ? p.getStartDate().toString() : null,
                    total);
        }
    }
}
