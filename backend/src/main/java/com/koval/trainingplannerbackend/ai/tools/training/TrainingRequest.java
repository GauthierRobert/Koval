package com.koval.trainingplannerbackend.ai.tools.training;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;

import java.util.List;


/** AI-facing compact DTO for creating or updating a training plan. Field names are abbreviated to reduce token cost. */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record TrainingRequest(
    @JsonPropertyDescription("CYCLING|RUNNING|SWIMMING|BRICK")
    String sport,
    @JsonPropertyDescription("Workout title")
    String title,
    @JsonPropertyDescription("Workout goal/summary")
    String desc,
    @JsonPropertyDescription("VO2MAX|THRESHOLD|SWEET_SPOT|ENDURANCE|SPRINT|RECOVERY|MIXED|TEST")
    String type,
    @JsonPropertyDescription("Estimated TSS (compute before calling)")
    Integer tss,
    @JsonPropertyDescription("Zone system ID if default exists for sport")
    String zoneSystemId,
    @JsonPropertyDescription("Ordered workout elements (blocks or sets)")
    List<WorkoutElementRequest> blocks,
    @JsonPropertyDescription("Group IDs (COACH only, when requested)")
    List<String> groupIds
) {}
