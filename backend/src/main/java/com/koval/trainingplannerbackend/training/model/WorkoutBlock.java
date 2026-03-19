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

        @JsonPropertyDescription("Short description to add relevant details")
        @JsonProperty(required = true)
        String description,

        // --- UNIFIED INTENSITY ---
        @JsonPropertyDescription("% of reference (FTP/Pace/CSS). Example: 90.")
        Integer intensityTarget,

        @JsonPropertyDescription("Start % for ramps.")
        Integer intensityStart,

        @JsonPropertyDescription("End % for ramps.")
        Integer intensityEnd,

        // --- CADENCE ---
        @JsonPropertyDescription("Target RPM (Bike/Run) or SPM (Swim).")
        Integer cadenceTarget,

        // --- ZONE-BASED TARGETING ---
        @JsonPropertyDescription("Zone label (e.g. 'Z3'). Use instead of intensityTarget for zone-based targeting.")
        String zoneTarget,

        @JsonPropertyDescription("Resolved display label (e.g. 'Z3 - Tempo (75-90%)'). Filled during enrichment, null on save.")
        String zoneLabel
) {

    public WorkoutBlock updateType(BlockType type) {
        return new WorkoutBlock(type, this.durationSeconds(), this.distanceMeters(), this.label(), this.description,
                this.intensityTarget(), this.intensityStart(), this.intensityEnd(), this.cadenceTarget(),
                this.zoneTarget(), this.zoneLabel());
    }

    public WorkoutBlock withResolvedIntensity(Integer resolvedIntensity, String resolvedZoneLabel) {
        return new WorkoutBlock(type, durationSeconds, distanceMeters, label, description,
                resolvedIntensity, intensityStart, intensityEnd, cadenceTarget, zoneTarget, resolvedZoneLabel);
    }

}