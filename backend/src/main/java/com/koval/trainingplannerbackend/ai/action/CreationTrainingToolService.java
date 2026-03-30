package com.koval.trainingplannerbackend.ai.action;

import com.koval.trainingplannerbackend.training.model.Training;
import com.koval.trainingplannerbackend.training.TrainingService;
import com.koval.trainingplannerbackend.training.tools.TrainingMapper;
import com.koval.trainingplannerbackend.training.tools.TrainingRequest;
import com.koval.trainingplannerbackend.training.tools.TrainingSummary;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Service;

@Service
public class CreationTrainingToolService {
    private final TrainingService trainingService;
    private final TrainingMapper trainingMapper;

    public CreationTrainingToolService(TrainingService trainingService, TrainingMapper trainingMapper) {
        this.trainingService = trainingService;
        this.trainingMapper = trainingMapper;
    }

    @Tool(description = "Create a new training workout.")
    public Object createTraining(
            @ToolParam(description = "Training to create") TrainingRequest create,
            @ToolParam(description = "Creator user ID") String userId) {
        ActionToolTracker.markCalled();
        Training training = trainingMapper.mapToEntity(create);
        return TrainingSummary.from(trainingService.createTraining(training, userId));
    }
}
