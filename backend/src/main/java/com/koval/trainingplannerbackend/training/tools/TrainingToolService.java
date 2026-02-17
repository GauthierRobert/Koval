package com.koval.trainingplannerbackend.training.tools;

import com.koval.trainingplannerbackend.training.model.Training;
import com.koval.trainingplannerbackend.training.TrainingService;
import com.koval.trainingplannerbackend.training.model.TrainingType;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * AI-facing tool service for Training operations.
 * Returns lean summaries to minimize token usage; use getTrainingDetails for full blocks.
 */
@Service
public class TrainingToolService {

    private final TrainingService trainingManagementService;
    private final TrainingMapper trainingMapper;

    public TrainingToolService(TrainingService trainingManagementService, TrainingMapper trainingMapper) {
        this.trainingManagementService = trainingManagementService;
        this.trainingMapper = trainingMapper;
    }

    @Tool(description = "List all training plans created by a specific user. Returns summaries — use getTrainingDetails to see full workout blocks.")
    public List<TrainingSummary> listTrainingsByUser(
            @ToolParam(description = "The user ID") String userId) {
        return trainingManagementService.listTrainingsByUser(userId).stream()
                .map(TrainingSummary::from).toList();
    }

    @Tool(description = "Get full details of a training plan including all workout blocks. Use this after listing trainings to drill down into a specific one.")
    public Training getTrainingDetails(
            @ToolParam(description = "The training ID to get full details for") String trainingId) {
        return trainingManagementService.getTrainingById(trainingId);
    }

    @Tool(description = "Search training plans by tag ID. Returns summaries.")
    public List<TrainingSummary> searchByTag(
            @ToolParam(description = "The tag ID to search by") String tag) {
        return trainingManagementService.searchByTag(tag).stream()
                .map(TrainingSummary::from).toList();
    }

    @Tool(description = "Search training plans by training type (VO2MAX, THRESHOLD, SWEET_SPOT, ENDURANCE, SPRINT, RECOVERY, MIXED, TEST). Returns summaries.")
    public List<TrainingSummary> searchByType(
            @ToolParam(description = "The training type to search by") TrainingType trainingType) {
        return trainingManagementService.searchByType(trainingType).stream()
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

    @Tool(description = "Delete a training plan by its ID")
    public String deleteTraining(
            @ToolParam(description = "The training ID to delete") String trainingId) {
        trainingManagementService.deleteTraining(trainingId);
        return "Training " + trainingId + " deleted successfully.";
    }
}
