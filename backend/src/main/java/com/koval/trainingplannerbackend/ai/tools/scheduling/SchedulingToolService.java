package com.koval.trainingplannerbackend.ai.tools.scheduling;

import com.koval.trainingplannerbackend.ai.tools.coach.ScheduleSummary;
import com.koval.trainingplannerbackend.auth.SecurityUtils;
import com.koval.trainingplannerbackend.coach.CoachService;
import com.koval.trainingplannerbackend.coach.ScheduledWorkout;
import com.koval.trainingplannerbackend.training.TrainingTitleResolver;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Service;

import java.time.LocalDate;

/**
 * Scheduling-specific tool for self-assigning trainings.
 * Wired only to the scheduling agent (Haiku).
 */
@Service
public class SchedulingToolService {

    private final CoachService coachService;
    private final TrainingTitleResolver trainingTitleResolver;

    public SchedulingToolService(CoachService coachService, TrainingTitleResolver trainingTitleResolver) {
        this.coachService = coachService;
        this.trainingTitleResolver = trainingTitleResolver;
    }

    @Tool(description = "Schedule a training for yourself on a date.")
    public ScheduleSummary selfAssignTraining(
            @ToolParam(description = "Training ID") String trainingId,
            @ToolParam(description = "Date (YYYY-MM-DD)") LocalDate scheduledDate,
            @ToolParam(description = "Notes (optional)") String notes,
            ToolContext context) {
        String userId = SecurityUtils.getUserId(context);
        ScheduledWorkout sw = coachService.selfAssignTraining(userId, trainingId, scheduledDate, notes);
        String title = trainingTitleResolver.resolveTitle(trainingId);
        return ScheduleSummary.from(sw, title);
    }
}
