package com.koval.trainingplannerbackend.mcp;

import com.koval.trainingplannerbackend.ai.tools.training.TrainingRequest;
import com.koval.trainingplannerbackend.ai.tools.training.TrainingToolService;
import com.koval.trainingplannerbackend.auth.SecurityUtils;
import com.koval.trainingplannerbackend.training.TrainingAccessService;
import com.koval.trainingplannerbackend.training.TrainingService;
import com.koval.trainingplannerbackend.training.model.Training;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * MCP tool adapter for Training CRUD operations.
 * Delegates to business services using SecurityUtils for auth context.
 */
@Service
public class McpTrainingTools {

    private final TrainingService trainingService;
    private final TrainingAccessService trainingAccessService;
    private final com.koval.trainingplannerbackend.ai.tools.training.TrainingMapper trainingMapper;

    public McpTrainingTools(TrainingService trainingService,
                            TrainingAccessService trainingAccessService,
                            com.koval.trainingplannerbackend.ai.tools.training.TrainingMapper trainingMapper) {
        this.trainingService = trainingService;
        this.trainingAccessService = trainingAccessService;
        this.trainingMapper = trainingMapper;
    }

    @Tool(description = "List the user's training workouts with pagination. Returns summaries including title, type, duration, and sport. Trainings are cycling/running/swimming/triathlon workout plans with structured blocks (warmup, intervals, steady, ramps, cooldown).")
    public List<McpTrainingSummary> listTrainings(
            @ToolParam(description = "Maximum number of trainings to return (default 15)") Integer limit,
            @ToolParam(description = "Number of items to skip for pagination (default 0)") Integer offset) {
        String userId = SecurityUtils.getCurrentUserId();
        int effectiveLimit = (limit != null && limit > 0) ? Math.min(limit, 50) : 15;
        int effectiveOffset = (offset != null && offset >= 0) ? offset : 0;
        int page = effectiveOffset / effectiveLimit;
        return trainingService.listTrainingsByUser(userId, PageRequest.of(page, effectiveLimit))
                .getContent().stream()
                .map(McpTrainingSummary::from)
                .toList();
    }

    @Tool(description = "Get full details of a specific training workout by its ID. Returns the complete workout structure with all blocks, durations, intensities, and metadata.")
    public Training getTraining(
            @ToolParam(description = "The training ID") String trainingId) {
        String userId = SecurityUtils.getCurrentUserId();
        Training training = trainingService.getTrainingById(trainingId);
        trainingAccessService.verifyAccess(userId, training);
        return training;
    }

    @Tool(description = "Create a new training workout plan. Requires a title and at least one workout block. Blocks can be: WARMUP, INTERVAL, STEADY, RAMP, COOLDOWN, FREE, PAUSE, TRANSITION. Intensities are expressed as percentage of FTP (cycling), threshold pace (running), or CSS (swimming). Use 'reps' and 'elements' for repeated sets.")
    public Object createTraining(
            @ToolParam(description = "The training to create") TrainingRequest create) {
        String validationError = TrainingToolService.validateTrainingRequest(create);
        if (validationError != null) return validationError;
        String userId = SecurityUtils.getCurrentUserId();
        Training training = trainingMapper.mapToEntity(create);
        return McpTrainingSummary.from(trainingService.createTraining(training, userId));
    }

    @Tool(description = "Update an existing training workout by ID. Provide the full updated training structure (title, blocks, etc.).")
    public Object updateTraining(
            @ToolParam(description = "The training ID to update") String trainingId,
            @ToolParam(description = "The updated training data") TrainingRequest updates) {
        String validationError = TrainingToolService.validateTrainingRequest(updates);
        if (validationError != null) return validationError;
        Training training = trainingMapper.mapToEntity(updates);
        return McpTrainingSummary.from(trainingService.updateTraining(trainingId, training));
    }

    @Tool(description = "Delete a training workout by ID. Only the creator can delete their own trainings.")
    public String deleteTraining(
            @ToolParam(description = "The training ID to delete") String trainingId) {
        String userId = SecurityUtils.getCurrentUserId();
        Training existing = trainingService.getTrainingById(trainingId);
        trainingAccessService.verifyAccess(userId, existing);
        String title = existing.getTitle();
        trainingService.deleteTraining(trainingId);
        return "Deleted training: " + title;
    }

    /** Richer summary for MCP consumers who don't have system prompt context. */
    public record McpTrainingSummary(String id, String title, String sport, String type,
                                     Integer durationMinutes, Integer estimatedTss,
                                     String description) {
        public static McpTrainingSummary from(Training t) {
            return new McpTrainingSummary(
                    t.getId(), t.getTitle(),
                    t.getSportType() != null ? t.getSportType().name() : null,
                    t.getTrainingType() != null ? t.getTrainingType().name() : null,
                    t.getEstimatedDurationSeconds() != null ? t.getEstimatedDurationSeconds() / 60 : null,
                    t.getEstimatedTss(),
                    t.getDescription());
        }
    }
}
