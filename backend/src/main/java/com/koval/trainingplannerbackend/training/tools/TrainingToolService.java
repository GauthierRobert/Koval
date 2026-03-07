package com.koval.trainingplannerbackend.training.tools;

import com.koval.trainingplannerbackend.training.TrainingService;
import com.koval.trainingplannerbackend.training.model.Training;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Sinks;

import java.util.List;
import java.util.Optional;

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
        emitToolCall(context, "createTraining", "Creating: " + create.title());
        Training training = trainingMapper.mapToEntity(create);
        TrainingSummary result = TrainingSummary.from(trainingManagementService.createTraining(training, userId));
        emitToolResult(context, "createTraining", result.title(), true);
        return result;
    }

    @Tool(description = "Update an existing training plan by its ID. Returns updated summary.")
    public TrainingSummary updateTraining(
            @ToolParam(description = "The training ID to update") String trainingId,
            @ToolParam(description = "The training fields to update") TrainingRequest updates,
            ToolContext context) {
        emitToolCall(context, "updateTraining", "Updating: " + updates.title());
        Training training = trainingMapper.mapToEntity(updates);
        TrainingSummary result = TrainingSummary.from(trainingManagementService.updateTraining(trainingId, training));
        emitToolResult(context, "updateTraining", result.title(), true);
        return result;
    }

    // ── Tool event helpers ───────────────────────────────────────────────

    private static void emitToolCall(ToolContext ctx, String name, String label) {
        getSink(ctx).ifPresent(s -> s.tryEmitNext(toolSse("tool_call", name, label, true)));
    }

    private static void emitToolResult(ToolContext ctx, String name, String label, boolean ok) {
        getSink(ctx).ifPresent(s -> s.tryEmitNext(toolSse("tool_result", name, label, ok)));
    }

    private static ServerSentEvent<String> toolSse(String event, String name, String label, boolean success) {
        String data = "{\"name\":\"%s\",\"label\":\"%s\",\"success\":%b}"
                .formatted(name, escapeJson(label), success);
        return ServerSentEvent.<String>builder().event(event).data(data).build();
    }

    @SuppressWarnings("unchecked")
    private static Optional<Sinks.Many<ServerSentEvent<String>>> getSink(ToolContext ctx) {
        if (ctx == null) return Optional.empty();
        return Optional.ofNullable(
                (Sinks.Many<ServerSentEvent<String>>) ctx.getContext().get("toolSink"));
    }

    private static String escapeJson(String s) {
        return s == null ? "" : s.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
