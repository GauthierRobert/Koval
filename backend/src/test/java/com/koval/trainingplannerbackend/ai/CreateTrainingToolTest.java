package com.koval.trainingplannerbackend.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.koval.trainingplannerbackend.BaseIntegrationTest;
import com.koval.trainingplannerbackend.ai.tools.training.TrainingRequest;
import com.koval.trainingplannerbackend.ai.tools.training.TrainingSummary;
import com.koval.trainingplannerbackend.ai.tools.training.TrainingToolService;
import com.koval.trainingplannerbackend.auth.SecurityUtils;
import com.koval.trainingplannerbackend.training.TrainingService;
import com.koval.trainingplannerbackend.training.model.Training;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.Map;

import static com.koval.trainingplannerbackend.training.model.BlockType.COOLDOWN;
import static com.koval.trainingplannerbackend.training.model.BlockType.WARMUP;

/**
 * Smoke test for the AI-facing createTraining tool with a swimming threshold workout.
 * Just deserializes the input, invokes the tool, and prints the result.
 */
class CreateTrainingToolTest extends BaseIntegrationTest {

    @Autowired
    private TrainingToolService trainingToolService;
    @Autowired
    private TrainingService trainingService;

    private static final String INPUT_JSON = """
            {
              "create": {
                "title": "Threshold 3×10×100 @ CSS — 4500m",
                "sport": "SWIMMING",
                "type": "THRESHOLD",
                "tss": 75,
                "desc": "4500m threshold session built around CSS (1:40/100m). 3000m of main set at CSS pace split into 3 rounds of 10×100 on 1:50 send-off, with 200m easy recovery between rounds. Designed to accumulate quality threshold volume while keeping form and turnover honest. Hold the send-off — if it slips by more than 2s, drop the last round to 8 reps.",
                "blocks": [
                  {"type": "WARMUP", "stroke": "FREESTYLE", "dist": 300, "label": "Z1", "desc": "Easy free, smooth and long"},
                  {"type": "WARMUP", "stroke": "DRILL", "dist": 200, "label": "Z1", "desc": "Drill — catch-up freestyle"},
                  {"type": "WARMUP", "stroke": "FREESTYLE", "dist": 100, "label": "Z2", "desc": "Build to CSS, last 25 fast"},
                  {"type": "INTERVAL", "reps": 4, "restDur": 20, "desc": "Pre-set: 4×100 mixed strokes",
                    "elements": [
                      {"type": "INTERVAL", "stroke": "CHOICE", "dist": 100, "pct": 90, "label": "Z2", "desc": "100 — 25 non-free / 75 free"}
                    ]
                  },
                  {"type": "INTERVAL", "reps": 10, "desc": "Round 1: 10×100 @ CSS on 1:50",
                    "elements": [
                      {"type": "INTERVAL", "stroke": "FREESTYLE", "dist": 100, "pct": 100, "sendOff": 110, "label": "Z4 Threshold", "desc": "100 free @ CSS"}
                    ]
                  },
                  {"type": "STEADY", "stroke": "CHOICE", "dist": 200, "pct": 70, "label": "Z1", "desc": "Easy recovery — loosen shoulders"},
                  {"type": "INTERVAL", "reps": 10, "desc": "Round 2: 10×100 @ CSS on 1:50",
                    "elements": [
                      {"type": "INTERVAL", "stroke": "FREESTYLE", "dist": 100, "pct": 100, "sendOff": 110, "label": "Z4 Threshold", "desc": "100 free @ CSS"}
                    ]
                  },
                  {"type": "STEADY", "stroke": "CHOICE", "dist": 200, "pct": 70, "label": "Z1", "desc": "Easy recovery — focus on breathing"},
                  {"type": "INTERVAL", "reps": 10, "desc": "Round 3: 10×100 @ CSS on 1:50 — hold form",
                    "elements": [
                      {"type": "INTERVAL", "stroke": "FREESTYLE", "dist": 100, "pct": 100, "sendOff": 110, "label": "Z4 Threshold", "desc": "100 free @ CSS"}
                    ]
                  },
                  {"type": "COOLDOWN", "stroke": "CHOICE", "dist": 500, "label": "Z1", "desc": "Easy mixed strokes, fully loose"}
                ]
              }
            }
            """;

    @Test
    @DisplayName("createTraining tool — swimming threshold 3x10x100 @ CSS")
    void createSwimmingThresholdTraining() throws Exception {
        JsonNode root = objectMapper.readTree(INPUT_JSON);
        TrainingRequest request = objectMapper.treeToValue(root.get("create"), TrainingRequest.class);

        ToolContext context = new ToolContext(Map.of(SecurityUtils.USER_ID_KEY, "athlete1"));

        TrainingSummary result = (TrainingSummary) trainingToolService.createTraining(request, context);

        System.out.println("===== createTraining result =====");
        Training trainingById = trainingService.getTrainingById(result.id());
        Assertions.assertEquals(WARMUP, trainingById.getBlocks().getFirst().type());
        Assertions.assertEquals(COOLDOWN, trainingById.getBlocks().getLast().type());
        System.out.println("=================================");
    }
}
