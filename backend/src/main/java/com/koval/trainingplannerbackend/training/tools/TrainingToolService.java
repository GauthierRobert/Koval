package com.koval.trainingplannerbackend.training.tools;

import com.koval.trainingplannerbackend.ai.ToolEventEmitter;
import com.koval.trainingplannerbackend.auth.SecurityUtils;
import com.koval.trainingplannerbackend.training.TrainingService;
import com.koval.trainingplannerbackend.training.model.Training;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * AI-facing tool service for creating and listing trainings.
 * Update/delete operations are in {@link TrainingModifyToolService}.
 */
@Service
public class TrainingToolService {

    private static final int MAX_INTENSITY_PERCENT = 250;

    private final TrainingService trainingManagementService;
    private final TrainingMapper trainingMapper;

    public TrainingToolService(TrainingService trainingManagementService,
                               TrainingMapper trainingMapper) {
        this.trainingManagementService = trainingManagementService;
        this.trainingMapper = trainingMapper;
    }

    /** Lists training plan summaries for a user with pagination support. */
    @Tool(description = "List user's training plans (default: 15 most recent).")
    public List<TrainingSummary> listTrainingsByUser(
            @ToolParam(description = "Max results (default 15)", required = false) Integer limit,
            @ToolParam(description = "Skip count (default 0)", required = false) Integer offset,
            ToolContext context) {
        String userId = SecurityUtils.getUserId(context);
        int effectiveLimit = (limit != null && limit > 0) ? limit : 15;
        int effectiveOffset = (offset != null && offset >= 0) ? offset : 0;
        int page = effectiveOffset / effectiveLimit;
        return trainingManagementService.listTrainingsByUser(userId, PageRequest.of(page, effectiveLimit))
                .getContent().stream()
                .map(TrainingSummary::from).toList();
    }

    /** Creates a training plan from a validated request and returns its summary. */
    @Tool(description = "Create a new training workout plan.")
    public Object createTraining(
            @ToolParam(description = "Training to create") TrainingRequest create,
            ToolContext context) {
        String validationError = validateTrainingRequest(create);
        if (validationError != null) {
            ToolEventEmitter.emitToolResult(context, "createTraining", "Validation failed", false);
            return validationError;
        }
        ToolEventEmitter.emitToolCall(context, "createTraining", "Creating: " + create.title());
        String userId = SecurityUtils.getUserId(context);
        Training training = trainingMapper.mapToEntity(create);
        TrainingSummary result = TrainingSummary.from(trainingManagementService.createTraining(training, userId));
        ToolEventEmitter.emitToolResult(context, "createTraining", result.title(), true);
        return result;
    }

    /** Validates title, blocks, and recursively validates all workout elements. Returns null if valid, error message otherwise. */
    public static String validateTrainingRequest(TrainingRequest request) {
        if (request.title() == null || request.title().isBlank()) {
            return "Error: title is required and cannot be blank.";
        }
        if (request.blocks() == null || request.blocks().isEmpty()) {
            return "Error: blocks list is required and cannot be empty.";
        }
        return validateElements(request.blocks(), "block");
    }

    // ── Private validation helpers ──────────────────────────────────────────

    private static String validateElements(List<WorkoutElementRequest> elements, String path) {
        for (int i = 0; i < elements.size(); i++) {
            WorkoutElementRequest element = elements.get(i);
            String prefix = path + "[" + i + "]";
            String error = element.isSet() ? validateSet(element, prefix) : validateLeaf(element, prefix);
            if (error != null) return error;
        }
        return null;
    }

    private static String validateSet(WorkoutElementRequest element, String prefix) {
        if (element.reps() == null || element.reps() < 1) {
            return "Error: " + prefix + " is a set but has invalid reps=" + element.reps() + " (must be >= 1).";
        }
        return validateElements(element.elements(), prefix + ".elements");
    }

    private static String validateLeaf(WorkoutElementRequest element, String prefix) {
        if (element.type() == null) {
            return "Error: " + prefix + " is missing required field 'type'.";
        }
        if (element.pct() != null && (element.pct() < 0 || element.pct() > MAX_INTENSITY_PERCENT)) {
            return "Error: " + prefix + " has out-of-range intensity pct=" + element.pct() + " (expected 0-" + MAX_INTENSITY_PERCENT + ").";
        }
        if (element.pctFrom() != null && (element.pctFrom() < 0 || element.pctFrom() > MAX_INTENSITY_PERCENT)) {
            return "Error: " + prefix + " has out-of-range pctFrom=" + element.pctFrom() + " (expected 0-" + MAX_INTENSITY_PERCENT + ").";
        }
        if (element.pctTo() != null && (element.pctTo() < 0 || element.pctTo() > MAX_INTENSITY_PERCENT)) {
            return "Error: " + prefix + " has out-of-range pctTo=" + element.pctTo() + " (expected 0-" + MAX_INTENSITY_PERCENT + ").";
        }
        if (element.dur() != null && element.dur() <= 0) {
            return "Error: " + prefix + " has invalid duration=" + element.dur() + " (must be > 0).";
        }
        if (element.dist() != null && element.dist() <= 0) {
            return "Error: " + prefix + " has invalid distance=" + element.dist() + " (must be > 0).";
        }
        return null;
    }
}
