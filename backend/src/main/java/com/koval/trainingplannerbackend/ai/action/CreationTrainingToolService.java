package com.koval.trainingplannerbackend.ai.action;

import com.koval.trainingplannerbackend.training.tools.TrainingRequest;
import com.koval.trainingplannerbackend.training.tools.TrainingToolService;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Service;

@Service
public class CreationTrainingToolService {
    private final TrainingToolService toolService;

    public CreationTrainingToolService(TrainingToolService toolService) {
        this.toolService = toolService;
    }

    @Tool(description = "Create a new training workout plan. Returns a summary with the new ID.")
    public Object createTraining(
            @ToolParam(description = "The training object to create") TrainingRequest create,
            @ToolParam(description = "The user ID of the creator") String userId,
            ToolContext context) {
        return toolService.createTraining(create, userId, context);
    }
}
