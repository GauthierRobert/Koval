package com.koval.trainingplannerbackend.training.tools;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.koval.trainingplannerbackend.training.model.BlockType;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record WorkoutBlockRequest(

        @JsonPropertyDescription("WARMUP|INTERVAL|STEADY|COOLDOWN|RAMP|FREE|PAUSE")
        @JsonProperty(required = true)
        BlockType type,

        @JsonPropertyDescription("sec")
        Integer dur,

        @JsonPropertyDescription("m")
        Integer dist,

        @JsonPropertyDescription("e.g. 'Z2'")
        @JsonProperty(required = true)
        String label,

        @JsonPropertyDescription("% ref e.g. 90")
        Integer pct,

        @JsonPropertyDescription("Ramp start %")
        Integer pctFrom,

        @JsonPropertyDescription("Ramp end %")
        Integer pctTo,

        @JsonPropertyDescription("RPM/SPM")
        Integer cad
) {}