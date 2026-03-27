package com.koval.trainingplannerbackend.training.tools;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;

import java.util.List;


/** AI-facing compact DTO for creating or updating a training plan. Field names are abbreviated to reduce token cost. */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record TrainingRequest(
    @JsonPropertyDescription("CYCLING|RUNNING|SWIMMING|BRICK")
    String sport,
    @JsonPropertyDescription("Workout title, e.g. 'VO2Max 5×3min'")
    String title,
    @JsonPropertyDescription("Workout goal / summary")
    String desc,
    @JsonPropertyDescription("VO2MAX|THRESHOLD|SWEET_SPOT|ENDURANCE|SPRINT|RECOVERY|MIXED|TEST")
    String type,
    @JsonPropertyDescription("Estimated TSS — compute before calling (see prompt rules)")
    Integer tss,
    @JsonPropertyDescription("Zone system ID — set when a default zone system exists for the sport")
    String zoneSystemId,
    @JsonPropertyDescription("Ordered list of workout elements (blocks or sets)")
    List<WorkoutElementRequest> blocks,
    @JsonPropertyDescription("Group IDs — ONLY for COACH role when explicitly requested, otherwise omit")
    List<String> groupIds
) {}