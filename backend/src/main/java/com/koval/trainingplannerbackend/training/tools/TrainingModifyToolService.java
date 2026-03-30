package com.koval.trainingplannerbackend.training.tools;

import com.koval.trainingplannerbackend.ai.ToolEventEmitter;
import com.koval.trainingplannerbackend.auth.SecurityUtils;
import com.koval.trainingplannerbackend.training.TrainingAccessService;
import com.koval.trainingplannerbackend.training.TrainingService;
import com.koval.trainingplannerbackend.training.model.Training;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Service;

/**
 * AI-facing tool service for updating and deleting trainings.
 * Separated from {@link TrainingToolService} so agents that only need
 * create/list don't pay the token cost of these extra tool schemas.
 */
@Service
public class TrainingModifyToolService {

    private final TrainingService trainingService;
    private final TrainingMapper trainingMapper;
    private final TrainingAccessService trainingAccessService;

    public TrainingModifyToolService(TrainingService trainingService,
                                     TrainingMapper trainingMapper,
                                     TrainingAccessService trainingAccessService) {
        this.trainingService = trainingService;
        this.trainingMapper = trainingMapper;
        this.trainingAccessService = trainingAccessService;
    }

    @Tool(description = "Update a training plan by ID.")
    public Object updateTraining(
            @ToolParam(description = "Training ID") String trainingId,
            @ToolParam(description = "Fields to update") TrainingRequest updates,
            ToolContext context) {
        String validationError = TrainingToolService.validateTrainingRequest(updates);
        if (validationError != null) {
            ToolEventEmitter.emitToolResult(context, "updateTraining", "Validation failed", false);
            return validationError;
        }
        ToolEventEmitter.emitToolCall(context, "updateTraining", "Updating: " + updates.title());
        Training training = trainingMapper.mapToEntity(updates);
        TrainingSummary result = TrainingSummary.from(trainingService.updateTraining(trainingId, training));
        ToolEventEmitter.emitToolResult(context, "updateTraining", result.title(), true);
        return result;
    }

    @Tool(description = "Delete a training plan by ID (creator only).")
    public String deleteTraining(
            @ToolParam(description = "Training ID") String trainingId,
            ToolContext context) {
        ToolEventEmitter.emitToolCall(context, "deleteTraining", "Deleting training...");
        String userId = SecurityUtils.getCurrentUserId();
        Training existing = trainingService.getTrainingById(trainingId);
        trainingAccessService.verifyAccess(userId, existing);
        String title = existing.getTitle();
        trainingService.deleteTraining(trainingId);
        ToolEventEmitter.emitToolResult(context, "deleteTraining", title, true);
        return "Deleted training: " + title;
    }
}
