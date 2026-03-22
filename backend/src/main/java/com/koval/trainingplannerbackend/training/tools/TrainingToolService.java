package com.koval.trainingplannerbackend.training.tools;

import com.koval.trainingplannerbackend.ai.ToolEventEmitter;
import com.koval.trainingplannerbackend.training.TrainingAccessService;
import com.koval.trainingplannerbackend.training.TrainingService;
import com.koval.trainingplannerbackend.training.model.Training;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * AI-facing tool service for Training operations.
 * Returns lean summaries to minimize token usage.
 */
@Service
public class TrainingToolService {

    private static final int MAX_INTENSITY_PERCENT = 250;

    private final TrainingService trainingManagementService;
    private final TrainingMapper trainingMapper;
    private final TrainingAccessService trainingAccessService;

    public TrainingToolService(TrainingService trainingManagementService,
                               TrainingMapper trainingMapper,
                               TrainingAccessService trainingAccessService) {
        this.trainingManagementService = trainingManagementService;
        this.trainingMapper = trainingMapper;
        this.trainingAccessService = trainingAccessService;
    }

    @Tool(description = "List training plans created by a specific user. Returns summaries. Use limit/offset for pagination (default: 15 most recent).")
    public List<TrainingSummary> listTrainingsByUser(
            @ToolParam(description = "The user ID") String userId,
            @ToolParam(description = "Max number of trainings to return (default 15)", required = false) Integer limit,
            @ToolParam(description = "Number of trainings to skip (default 0)", required = false) Integer offset) {
        int effectiveLimit = (limit != null && limit > 0) ? limit : 15;
        int effectiveOffset = (offset != null && offset >= 0) ? offset : 0;
        int page = effectiveLimit > 0 ? effectiveOffset / effectiveLimit : 0;
        return trainingManagementService.listTrainingsByUser(userId, PageRequest.of(page, effectiveLimit))
                .getContent().stream()
                .map(TrainingSummary::from).toList();
    }

    @Tool(description = "Create a new training workout plan. Returns a summary with the new ID.")
    public Object createTraining(
            @ToolParam(description = "The training object to create") TrainingRequest create,
            @ToolParam(description = "The user ID of the creator") String userId,
            ToolContext context) {
        String validationError = validateTrainingRequest(create);
        if (validationError != null) {
            ToolEventEmitter.emitToolResult(context, "createTraining", "Validation failed", false);
            return validationError;
        }
        ToolEventEmitter.emitToolCall(context, "createTraining", "Creating: " + create.title());
        Training training = trainingMapper.mapToEntity(create);
        TrainingSummary result = TrainingSummary.from(trainingManagementService.createTraining(training, userId));
        ToolEventEmitter.emitToolResult(context, "createTraining", result.title(), true);
        return result;
    }

    @Tool(description = "Update an existing training plan by its ID. Returns updated summary.")
    public Object updateTraining(
            @ToolParam(description = "The training ID to update") String trainingId,
            @ToolParam(description = "The training fields to update") TrainingRequest updates,
            ToolContext context) {
        String validationError = validateTrainingRequest(updates);
        if (validationError != null) {
            ToolEventEmitter.emitToolResult(context, "updateTraining", "Validation failed", false);
            return validationError;
        }
        ToolEventEmitter.emitToolCall(context, "updateTraining", "Updating: " + updates.title());
        Training training = trainingMapper.mapToEntity(updates);
        TrainingSummary result = TrainingSummary.from(trainingManagementService.updateTraining(trainingId, training));
        ToolEventEmitter.emitToolResult(context, "updateTraining", result.title(), true);
        return result;
    }

    public static String validateTrainingRequest(TrainingRequest request) {
        if (request.title() == null || request.title().isBlank()) {
            return "Error: title is required and cannot be blank.";
        }
        if (request.blocks() == null || request.blocks().isEmpty()) {
            return "Error: blocks list is required and cannot be empty.";
        }
        return validateElements(request.blocks(), "block");
    }

    private static String validateElements(java.util.List<WorkoutElementRequest> elements, String path) {
        for (int i = 0; i < elements.size(); i++) {
            WorkoutElementRequest b = elements.get(i);
            String prefix = path + "[" + i + "]";

            if (b.isSet()) {
                // Validate set
                if (b.reps() == null || b.reps() < 1) {
                    return "Error: " + prefix + " is a set but has invalid reps=" + b.reps() + " (must be >= 1).";
                }
                String childError = validateElements(b.elements(), prefix + ".elements");
                if (childError != null) return childError;
            } else {
                // Validate leaf
                if (b.type() == null) {
                    return "Error: " + prefix + " is missing required field 'type'.";
                }
                if (b.pct() != null && (b.pct() < 0 || b.pct() > MAX_INTENSITY_PERCENT)) {
                    return "Error: " + prefix + " has out-of-range intensity pct=" + b.pct() + " (expected 0-" + MAX_INTENSITY_PERCENT + ").";
                }
                if (b.pctFrom() != null && (b.pctFrom() < 0 || b.pctFrom() > MAX_INTENSITY_PERCENT)) {
                    return "Error: " + prefix + " has out-of-range pctFrom=" + b.pctFrom() + " (expected 0-" + MAX_INTENSITY_PERCENT + ").";
                }
                if (b.pctTo() != null && (b.pctTo() < 0 || b.pctTo() > MAX_INTENSITY_PERCENT)) {
                    return "Error: " + prefix + " has out-of-range pctTo=" + b.pctTo() + " (expected 0-" + MAX_INTENSITY_PERCENT + ").";
                }
                if (b.dur() != null && b.dur() <= 0) {
                    return "Error: " + prefix + " has invalid duration=" + b.dur() + " (must be > 0).";
                }
                if (b.dist() != null && b.dist() <= 0) {
                    return "Error: " + prefix + " has invalid distance=" + b.dist() + " (must be > 0).";
                }
            }
        }
        return null;
    }

    @Tool(description = "Delete a training plan by its ID. Only the creator can delete their own training.")
    public String deleteTraining(
            @ToolParam(description = "The training ID to delete") String trainingId,
            @ToolParam(description = "The user ID (ownership check)") String userId,
            ToolContext context) {
        ToolEventEmitter.emitToolCall(context, "deleteTraining", "Deleting training...");
        Training existing = trainingManagementService.getTrainingById(trainingId);
        trainingAccessService.verifyAccess(userId, existing);
        String title = existing.getTitle();
        trainingManagementService.deleteTraining(trainingId);
        ToolEventEmitter.emitToolResult(context, "deleteTraining", title, true);
        return "Deleted training: " + title;
    }
}
