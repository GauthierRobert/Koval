package com.koval.trainingplannerbackend.mcp;

import com.koval.trainingplannerbackend.auth.SecurityUtils;
import com.koval.trainingplannerbackend.training.TrainingAccessService;
import com.koval.trainingplannerbackend.training.TrainingService;
import com.koval.trainingplannerbackend.training.metrics.TrainingMetricsService;
import com.koval.trainingplannerbackend.training.model.Training;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * MCP tool adapter for Training CRUD operations.
 * Delegates to business services using SecurityUtils for auth context.
 *
 * <p>For create/update, MCP clients send the verbose {@link McpTrainingInput} (mirrors
 * the {@code Training} entity field-for-field) rather than the abbreviated AI-internal DTO,
 * so the tool schema is self-documenting for external Claude clients.</p>
 */
@Service
public class McpTrainingTools {

    private final TrainingService trainingService;
    private final TrainingAccessService trainingAccessService;
    private final TrainingMetricsService trainingMetricsService;
    private final McpTrainingMapper mcpTrainingMapper;

    public McpTrainingTools(TrainingService trainingService,
                            TrainingAccessService trainingAccessService,
                            TrainingMetricsService trainingMetricsService,
                            McpTrainingMapper mcpTrainingMapper) {
        this.trainingService = trainingService;
        this.trainingAccessService = trainingAccessService;
        this.trainingMetricsService = trainingMetricsService;
        this.mcpTrainingMapper = mcpTrainingMapper;
    }

    @Tool(description = """
            Query the user's training workouts by optional filters, with pagination. Trainings are
            cycling/running/swimming/triathlon workout plans with structured blocks (warmup, intervals,
            steady, ramps, cooldown). All filters are optional — pass null to skip a filter. When no
            filters are given this returns the most recent trainings. Returns summaries (id, title, sport,
            type, duration in minutes, estimated TSS, description).""")
    public List<McpTrainingSummary> queryTrainings(
            @ToolParam(description = "Title substring to match (case-insensitive). Null or empty = no title filter.") String query,
            @ToolParam(description = "Sport filter: CYCLING, RUNNING, SWIMMING, BRICK. Null = any sport.") String sport,
            @ToolParam(description = "Minimum duration in minutes. Null = no minimum.") Integer minDurationMin,
            @ToolParam(description = "Maximum duration in minutes. Null = no maximum.") Integer maxDurationMin,
            @ToolParam(description = "Maximum number of trainings to return (default 15, max 50)") Integer limit,
            @ToolParam(description = "Number of matching items to skip for pagination (default 0)") Integer offset) {
        String userId = SecurityUtils.getCurrentUserId();
        int effectiveLimit = (limit != null && limit > 0) ? Math.min(limit, 50) : 15;
        int effectiveOffset = (offset != null && offset >= 0) ? offset : 0;
        String q = (query != null && !query.isBlank()) ? query.toLowerCase() : null;
        String s = (sport != null && !sport.isBlank()) ? sport.toUpperCase() : null;
        return trainingService.listTrainingsByUser(userId).stream()
                .filter(t -> q == null || (t.getTitle() != null && t.getTitle().toLowerCase().contains(q)))
                .filter(t -> s == null || (t.getSportType() != null && t.getSportType().name().equals(s)))
                .filter(t -> {
                    if (minDurationMin == null && maxDurationMin == null) return true;
                    Integer secs = t.getEstimatedDurationSeconds();
                    if (secs == null) return false;
                    int mins = secs / 60;
                    if (minDurationMin != null && mins < minDurationMin) return false;
                    if (maxDurationMin != null && mins > maxDurationMin) return false;
                    return true;
                })
                .skip(effectiveOffset)
                .limit(effectiveLimit)
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

    @Tool(description = """
            Create a new training workout plan. Input mirrors the Training entity:
            top-level fields are sportType, title, description, trainingType, blocks (ordered list of WorkoutElement),
            and optional groupIds. Each WorkoutElement is either a leaf block (type + durationSeconds or
            distanceMeters + label + intensityTarget) or a set (repetitions + elements + optional rest).
            Block types: WARMUP, STEADY, INTERVAL, COOLDOWN, RAMP, FREE, PAUSE, TRANSITION. Intensities are
            % of FTP (cycling), threshold pace (running), or CSS (swimming). For sets, restDurationSeconds and
            restIntensity follow the manual builder UI: null/0 duration = no rest; duration > 0 with null/0
            intensity = passive rest (full pause); duration > 0 with intensity > 0 = active rest at that %.
            """)
    public Object createTraining(
            @ToolParam(description = "The training to create (verbose schema mirroring the Training entity)") McpTrainingInput create) {
        String validationError = McpTrainingMapper.validate(create);
        if (validationError != null) return validationError;
        String userId = SecurityUtils.getCurrentUserId();
        Training training = mcpTrainingMapper.mapToEntity(create);
        return McpTrainingSummary.from(trainingService.createTraining(training, userId));
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

    public record TrainingMetricsEstimate(String trainingId, String title, Integer estimatedTss,
                                           Double estimatedIf, Integer estimatedDurationSeconds,
                                           Integer estimatedDistance) {}

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
