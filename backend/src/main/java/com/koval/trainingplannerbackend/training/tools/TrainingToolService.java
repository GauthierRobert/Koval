package com.koval.trainingplannerbackend.training.tools;

import com.koval.trainingplannerbackend.ai.ToolEventEmitter;
import com.koval.trainingplannerbackend.training.TrainingService;
import com.koval.trainingplannerbackend.training.model.Training;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * AI-facing tool service for Training operations.
 * Returns lean summaries to minimize token usage.
 */
@Service
public class TrainingToolService {

    private final TrainingService trainingManagementService;
    private final TrainingMapper trainingMapper;

    public TrainingToolService(TrainingService trainingManagementService, TrainingMapper trainingMapper) {
        this.trainingManagementService = trainingManagementService;
        this.trainingMapper = trainingMapper;
    }

    @Tool(description = "List all training plans created by a specific user. Returns summaries.")
    public List<TrainingSummary> listTrainingsByUser(
            @ToolParam(description = "The user ID") String userId) {
        return trainingManagementService.listTrainingsByUser(userId).stream()
                .map(TrainingSummary::from).toList();
    }

    @Tool(description = "Create a new training workout plan. Returns a summary with the new ID.")
    public TrainingSummary createTraining(
            @ToolParam(description = "The training object to create") TrainingRequest create,
            @ToolParam(description = "The user ID of the creator") String userId,
            ToolContext context) {
        ToolEventEmitter.emitToolCall(context, "createTraining", "Creating: " + create.title());
        Training training = trainingMapper.mapToEntity(create);
        TrainingSummary result = TrainingSummary.from(trainingManagementService.createTraining(training, userId));
        ToolEventEmitter.emitToolResult(context, "createTraining", result.title(), true);
        return result;
    }

    @Tool(description = "Update an existing training plan by its ID. Returns updated summary.")
    public TrainingSummary updateTraining(
            @ToolParam(description = "The training ID to update") String trainingId,
            @ToolParam(description = "The training fields to update") TrainingRequest updates,
            ToolContext context) {
        ToolEventEmitter.emitToolCall(context, "updateTraining", "Updating: " + updates.title());
        Training training = trainingMapper.mapToEntity(updates);
        TrainingSummary result = TrainingSummary.from(trainingManagementService.updateTraining(trainingId, training));
        ToolEventEmitter.emitToolResult(context, "updateTraining", result.title(), true);
        return result;
    }

    @Tool(description = "Delete a training plan by its ID. Only the creator can delete their own training.")
    public String deleteTraining(
            @ToolParam(description = "The training ID to delete") String trainingId,
            @ToolParam(description = "The user ID (ownership check)") String userId,
            ToolContext context) {
        ToolEventEmitter.emitToolCall(context, "deleteTraining", "Deleting training...");
        Training existing = trainingManagementService.getTrainingById(trainingId);
        if (!userId.equals(existing.getCreatedBy())) {
            ToolEventEmitter.emitToolResult(context, "deleteTraining", "Not authorized", false);
            throw new IllegalStateException("Training does not belong to user " + userId);
        }
        String title = existing.getTitle();
        trainingManagementService.deleteTraining(trainingId);
        ToolEventEmitter.emitToolResult(context, "deleteTraining", title, true);
        return "Deleted training: " + title;
    }
}
