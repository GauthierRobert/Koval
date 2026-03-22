package com.koval.trainingplannerbackend.ai.action;

import com.koval.trainingplannerbackend.ai.ToolEventEmitter;
import com.koval.trainingplannerbackend.training.TrainingService;
import com.koval.trainingplannerbackend.training.model.Training;
import com.koval.trainingplannerbackend.training.tools.TrainingMapper;
import com.koval.trainingplannerbackend.training.tools.TrainingRequest;
import com.koval.trainingplannerbackend.training.tools.TrainingSummary;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;

import static com.koval.trainingplannerbackend.training.tools.TrainingToolService.validateTrainingRequest;

public class CreationTrainingToolService {
    private final TrainingService trainingManagementService;
    private final TrainingMapper trainingMapper;

    public CreationTrainingToolService(TrainingService trainingManagementService, TrainingMapper trainingMapper) {
        this.trainingManagementService = trainingManagementService;
        this.trainingMapper = trainingMapper;
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
}
