package com.koval.trainingplannerbackend.mcp;

import com.koval.trainingplannerbackend.auth.SecurityUtils;
import com.koval.trainingplannerbackend.coach.CoachService;
import com.koval.trainingplannerbackend.coach.ScheduledWorkout;
import com.koval.trainingplannerbackend.coach.ScheduledWorkoutService;
import com.koval.trainingplannerbackend.training.TrainingService;
import com.koval.trainingplannerbackend.training.model.Training;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;

/**
 * MCP tool adapter for scheduling operations (self-assign, mark complete, etc.).
 */
@Service
public class McpSchedulingTools {

    private final CoachService coachService;
    private final ScheduledWorkoutService scheduledWorkoutService;
    private final TrainingService trainingService;

    public McpSchedulingTools(CoachService coachService,
                              ScheduledWorkoutService scheduledWorkoutService,
                              TrainingService trainingService) {
        this.coachService = coachService;
        this.scheduledWorkoutService = scheduledWorkoutService;
        this.trainingService = trainingService;
    }

    @Tool(description = "Schedule a training workout for yourself on a specific date. The training must exist first (use listTrainings or createTraining).")
    public ScheduleSummary scheduleTraining(
            @ToolParam(description = "Training ID to schedule") String trainingId,
            @ToolParam(description = "Date to schedule on (YYYY-MM-DD)") LocalDate date,
            @ToolParam(description = "Optional notes for this scheduled workout") String notes) {
        String userId = SecurityUtils.getCurrentUserId();
        ScheduledWorkout sw = coachService.selfAssignTraining(userId, trainingId, date, notes);
        String title = trainingService.getTrainingById(trainingId).getTitle();
        return ScheduleSummary.from(sw, title);
    }

    @Tool(description = "Get your scheduled workouts within a date range. Returns all planned, completed, and skipped workouts for the period.")
    public List<ScheduleSummary> getMySchedule(
            @ToolParam(description = "Start date inclusive (YYYY-MM-DD)") LocalDate from,
            @ToolParam(description = "End date inclusive (YYYY-MM-DD)") LocalDate to) {
        String userId = SecurityUtils.getCurrentUserId();
        return scheduledWorkoutService.getAthleteSchedule(userId, from, to).stream()
                .map(sw -> {
                    String title = resolveTitle(sw.getTrainingId());
                    return ScheduleSummary.from(sw, title);
                }).toList();
    }

    @Tool(description = "Mark a scheduled workout as completed.")
    public ScheduleSummary markCompleted(
            @ToolParam(description = "Scheduled workout ID") String scheduledWorkoutId) {
        ScheduledWorkout sw = scheduledWorkoutService.markCompleted(scheduledWorkoutId, null, null);
        return ScheduleSummary.from(sw, resolveTitle(sw.getTrainingId()));
    }

    @Tool(description = "Mark a scheduled workout as skipped.")
    public ScheduleSummary markSkipped(
            @ToolParam(description = "Scheduled workout ID") String scheduledWorkoutId) {
        ScheduledWorkout sw = scheduledWorkoutService.markSkipped(scheduledWorkoutId);
        return ScheduleSummary.from(sw, resolveTitle(sw.getTrainingId()));
    }

    @Tool(description = "Move a scheduled workout to a different date. The status (PENDING/COMPLETED/SKIPPED) is preserved.")
    public ScheduleSummary rescheduleWorkout(
            @ToolParam(description = "Scheduled workout ID") String scheduledWorkoutId,
            @ToolParam(description = "New date (YYYY-MM-DD)") LocalDate newDate) {
        if (newDate == null) return null;
        ScheduledWorkout sw = scheduledWorkoutService.reschedule(scheduledWorkoutId, newDate);
        return ScheduleSummary.from(sw, resolveTitle(sw.getTrainingId()));
    }

    @Tool(description = "Permanently delete a scheduled workout from the calendar (un-assign). Use this when the user wants to cancel a planned session entirely. To just record skipping it, use markSkipped instead.")
    public String unassignWorkout(
            @ToolParam(description = "Scheduled workout ID to remove") String scheduledWorkoutId) {
        scheduledWorkoutService.deleteScheduledWorkout(scheduledWorkoutId);
        return "Scheduled workout removed.";
    }

    @Tool(description = "Get full detail of a single scheduled workout: id, training id, resolved training title, scheduled date, status, notes.")
    public ScheduleSummary getScheduledWorkoutDetail(
            @ToolParam(description = "Scheduled workout ID") String scheduledWorkoutId) {
        ScheduledWorkout sw = scheduledWorkoutService.getScheduledWorkout(scheduledWorkoutId);
        return ScheduleSummary.from(sw, resolveTitle(sw.getTrainingId()));
    }

    private String resolveTitle(String trainingId) {
        if (trainingId == null) return "Unknown";
        try {
            return trainingService.getTrainingById(trainingId).getTitle();
        } catch (Exception e) {
            return "Unknown";
        }
    }

    public record ScheduleSummary(String id, String trainingId, String title, String date, String status, String notes) {
        public static ScheduleSummary from(ScheduledWorkout sw, String title) {
            return new ScheduleSummary(
                    sw.getId(), sw.getTrainingId(), title,
                    sw.getScheduledDate() != null ? sw.getScheduledDate().toString() : null,
                    sw.getStatus() != null ? sw.getStatus().name() : null,
                    sw.getNotes());
        }
    }
}
