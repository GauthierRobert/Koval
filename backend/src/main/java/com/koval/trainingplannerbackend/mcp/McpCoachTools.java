package com.koval.trainingplannerbackend.mcp;

import com.koval.trainingplannerbackend.auth.SecurityUtils;
import com.koval.trainingplannerbackend.coach.CoachService;
import com.koval.trainingplannerbackend.coach.ScheduledWorkout;
import com.koval.trainingplannerbackend.coach.ScheduledWorkoutService;
import com.koval.trainingplannerbackend.coach.dto.AthleteResponse;
import com.koval.trainingplannerbackend.training.TrainingService;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;

/**
 * MCP tool adapter for coach operations.
 * Only usable by users with COACH role.
 */
@Service
public class McpCoachTools {

    private final CoachService coachService;
    private final ScheduledWorkoutService scheduledWorkoutService;
    private final TrainingService trainingService;

    public McpCoachTools(CoachService coachService,
                         ScheduledWorkoutService scheduledWorkoutService,
                         TrainingService trainingService) {
        this.coachService = coachService;
        this.scheduledWorkoutService = scheduledWorkoutService;
        this.trainingService = trainingService;
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

    private String resolveTitle(String trainingId) {
        if (trainingId == null) return "Unknown";
        try {
            return trainingService.getTrainingById(trainingId).getTitle();
        } catch (Exception e) {
            return "Unknown";
        }
    }
}
