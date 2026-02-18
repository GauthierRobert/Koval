package com.koval.trainingplannerbackend.training.tools;

import com.koval.trainingplannerbackend.training.model.Training;
import com.koval.trainingplannerbackend.training.TrainingService;
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
            @ToolParam(description = "The user ID of the creator") String userId) {
        Training training = trainingMapper.mapToEntity(create);
        return TrainingSummary.from(trainingManagementService.createTraining(training, userId));
    }

    @Tool(description = "Update an existing training plan by its ID. Returns updated summary.")
    public TrainingSummary updateTraining(
            @ToolParam(description = "The training ID to update") String trainingId,
            @ToolParam(description = "The training fields to update") TrainingRequest updates) {
        Training training = trainingMapper.mapToEntity(updates);
        return TrainingSummary.from(trainingManagementService.updateTraining(trainingId, training));
    }
}
