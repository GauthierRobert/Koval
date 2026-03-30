package com.koval.trainingplannerbackend.ai;

import com.koval.trainingplannerbackend.auth.SecurityUtils;
import com.koval.trainingplannerbackend.coach.CoachService;
import com.koval.trainingplannerbackend.coach.ScheduledWorkout;
import com.koval.trainingplannerbackend.coach.tools.ScheduleSummary;
import com.koval.trainingplannerbackend.training.TrainingRepository;
import com.koval.trainingplannerbackend.training.model.Training;
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
    private final TrainingRepository trainingRepository;

    public SchedulingToolService(CoachService coachService, TrainingRepository trainingRepository) {
        this.coachService = coachService;
        this.trainingRepository = trainingRepository;
    }

    @Tool(description = "Schedule a training for yourself on a date.")
    public ScheduleSummary selfAssignTraining(
            @ToolParam(description = "Training ID") String trainingId,
            @ToolParam(description = "Date (YYYY-MM-DD)") LocalDate scheduledDate,
            @ToolParam(description = "Notes (optional)") String notes,
            ToolContext context) {
        String userId = SecurityUtils.getUserId(context);
        ScheduledWorkout sw = coachService.selfAssignTraining(userId, trainingId, scheduledDate, notes);
        String title = trainingRepository.findById(trainingId)
                .map(Training::getTitle).orElse("Unknown");
        return ScheduleSummary.from(sw, title);
    }
}
