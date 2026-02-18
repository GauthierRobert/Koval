package com.koval.trainingplannerbackend.training.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record WorkoutBlock(

        @JsonPropertyDescription("WARMUP, INTERVAL, STEADY, COOLDOWN, RAMP, FREE, PAUSE")
        @JsonProperty(required = true)
        BlockType type,

        @JsonPropertyDescription("Duration (sec). Omit if distance set.")
        Integer durationSeconds,

        @JsonPropertyDescription("Distance (m). Omit if duration set.")
        Integer distanceMeters,

        @JsonPropertyDescription("Label (e.g., 'Z2 Endurance').")
        @JsonProperty(required = true)
        String label,

        // --- UNIFIED INTENSITY ---
        @JsonPropertyDescription("% of reference (FTP/Pace/CSS). Example: 90.")
        Integer intensityTarget,

        @JsonPropertyDescription("Start % for ramps.")
        Integer intensityStart,

        @JsonPropertyDescription("End % for ramps.")
        Integer intensityEnd,

        // --- CADENCE ---
        @JsonPropertyDescription("Target RPM (Bike/Run) or SPM (Swim).")
        Integer cadenceTarget
) {

    public WorkoutBlock updateType(BlockType type) {
        return new WorkoutBlock(type, this.durationSeconds(), this.distanceMeters(), this.label(), this.intensityTarget(), this.intensityStart(), this.intensityEnd(), this.cadenceTarget());
    }

}