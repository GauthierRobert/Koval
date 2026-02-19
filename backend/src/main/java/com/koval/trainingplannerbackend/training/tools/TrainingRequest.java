package com.koval.trainingplannerbackend.training.tools;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;

import java.util.List;


@JsonInclude(JsonInclude.Include.NON_NULL)
public record TrainingRequest(
    @JsonPropertyDescription("CYCLING|RUNNING|SWIMMING|BRICK")
    String sport,

    @JsonPropertyDescription("Title e.g. 'VO2Max 5x3min'")
    String title,

    @JsonPropertyDescription("Goal")
    String desc,

    @JsonPropertyDescription("VO2MAX|THRESHOLD|SWEET_SPOT|ENDURANCE|RECOVERY|MIXED|TEST")
    String type,

    @JsonPropertyDescription("TSS")
    Integer tss,

    @JsonPropertyDescription("Zone system id")
    String zoneSystemId,

    @JsonPropertyDescription("Blocks")
    List<WorkoutBlockRequest> blocks,

    @JsonPropertyDescription("Tags e.g. 'club-btc'")
    List<String> tags
) {}