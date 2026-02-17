package com.koval.trainingplannerbackend.training.tools;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.koval.trainingplannerbackend.training.model.WorkoutBlock;

import java.util.List;


@JsonInclude(JsonInclude.Include.NON_NULL)
public record TrainingRequest(
    @JsonPropertyDescription("Sport type: CYCLING, RUNNING, SWIMMING, BRICK")
    String sportType,

    @JsonPropertyDescription("Workout title (e.g. 'VO2Max 5x3min')")
    String title,

    @JsonPropertyDescription("Goal description")
    String description,

    @JsonPropertyDescription("Focus: VO2MAX, THRESHOLD, SWEET_SPOT, ENDURANCE, RECOVERY, MIXED, TEST")
    String trainingType,

    @JsonPropertyDescription("Estimated TSS")
    Integer estimatedTss,

    @JsonPropertyDescription("List of intervals")
    List<WorkoutBlock> blocks,
    
    @JsonPropertyDescription("Tags (e.g. 'club-btc')")
    List<String> tags
) {}