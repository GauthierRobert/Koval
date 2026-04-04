package com.koval.trainingplannerbackend.ai.tools.coach;

import com.koval.trainingplannerbackend.ai.ToolEventEmitter;
import com.koval.trainingplannerbackend.ai.anonymization.AnonymizationContext;
import com.koval.trainingplannerbackend.ai.anonymization.AnonymizationService;
import com.koval.trainingplannerbackend.auth.SecurityUtils;
import com.koval.trainingplannerbackend.coach.CoachGroupService;
import com.koval.trainingplannerbackend.coach.CoachService;
import com.koval.trainingplannerbackend.coach.ScheduledWorkout;
import com.koval.trainingplannerbackend.training.TrainingRepository;
import com.koval.trainingplannerbackend.training.model.Training;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;

/**
 * AI-facing tool service for Coach operations.
 * Returns lean summaries to minimize token usage.
 */
@Service
public class CoachToolService {

    private final CoachService coachService;
    private final CoachGroupService coachGroupService;
    private final TrainingRepository trainingRepository;

    public CoachToolService(CoachService coachService, CoachGroupService coachGroupService, TrainingRepository trainingRepository) {
        this.coachService = coachService;
        this.coachGroupService = coachGroupService;
        this.trainingRepository = trainingRepository;
    }

    @Tool(description = "Assign a training to one or more athletes on a date.")
    public Object assignTraining(
            @ToolParam(description = "Training Id") String trainingId,
            @ToolParam(description = "Athlete Ids") List<String> athleteIds,
            @ToolParam(description = "Date (YYYY-MM-DD)") LocalDate scheduledDate,
            @ToolParam(description = "Notes (optional)") String notes,
            ToolContext context) {
        if (trainingId == null || trainingId.isBlank()) {
            ToolEventEmitter.emitToolResult(context, "assignTraining", "Validation failed", false);
            return "Error: trainingId is required.";
        }
        if (athleteIds == null || athleteIds.isEmpty()) {
            ToolEventEmitter.emitToolResult(context, "assignTraining", "Validation failed", false);
            return "Error: athleteIds list is required and cannot be empty.";
        }
        if (scheduledDate == null) {
            ToolEventEmitter.emitToolResult(context, "assignTraining", "Validation failed", false);
            return "Error: scheduledDate is required.";
        }
        ToolEventEmitter.emitToolCall(context, "assignTraining", "Scheduling for " + athleteIds.size() + " athlete(s)...");
        String coachId = SecurityUtils.getUserId(context);
        // De-anonymize athlete aliases (e.g. "Athlete-1") back to real IDs
        AnonymizationContext anonCtx = AnonymizationService.fromToolContext(context.getContext());
        List<String> realAthleteIds = anonCtx != null ? anonCtx.deAnonymizeAll(athleteIds) : athleteIds;
        List<ScheduledWorkout> workouts = coachService.assignTraining(coachId, trainingId, realAthleteIds, scheduledDate, notes, null);
        String title = resolveTrainingTitle(trainingId);
        List<ScheduleSummary> result = workouts.stream().map(sw -> ScheduleSummary.from(sw, title)).toList();
        ToolEventEmitter.emitToolResult(context, "assignTraining", "Assigned to " + result.size() + " athlete(s)", true);
        return result;
    }

    @Tool(description = "List coach's athletes filtered by group.")
    public List<AthleteSummary> getAthletesByGroup(
            @ToolParam(description = "Group ID") String groupId,
            ToolContext context) {
        String coachId = SecurityUtils.getUserId(context);
        AnonymizationContext anonCtx = AnonymizationService.fromToolContext(context.getContext());
        // De-anonymize group alias back to real ID
        String realGroupId = anonCtx != null ? anonCtx.deAnonymize(groupId) : groupId;
        return coachGroupService.getAthletesByGroup(coachId, realGroupId).stream()
                .map(u -> {
                    // Anonymize athlete identity in the response
                    if (anonCtx != null) {
                        String alias = anonCtx.anonymizeAthlete(u.getId());
                        return new AthleteSummary(alias, alias);
                    }
                    return AthleteSummary.from(u);
                }).toList();
    }

    private String resolveTrainingTitle(String trainingId) {
        return trainingRepository.findById(trainingId)
                .map(Training::getTitle).orElse("Unknown");
    }
}
