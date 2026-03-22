package com.koval.trainingplannerbackend.training.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;

import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record WorkoutElement(

        // ── SET FIELDS (non-null when this is a repeatable group) ──

        @JsonPropertyDescription("Number of repetitions for this set (e.g. 10 for '10x...')")
        Integer repetitions,

        @JsonPropertyDescription("Child elements (blocks or nested sets)")
        List<WorkoutElement> elements,

        @JsonPropertyDescription("Passive rest duration (sec) between repetitions")
        Integer restDurationSeconds,

        @JsonPropertyDescription("Intensity % during rest between repetitions (default ~40)")
        Integer restIntensity,

        // ── LEAF FIELDS (same as former WorkoutBlock) ──

        @JsonPropertyDescription("WARMUP, INTERVAL, STEADY, COOLDOWN, RAMP, FREE, PAUSE. Required for leaf blocks, null for sets.")
        BlockType type,

        @JsonPropertyDescription("Duration (sec). Omit if distance set.")
        Integer durationSeconds,

        @JsonPropertyDescription("Distance (m). Omit if duration set.")
        Integer distanceMeters,

        @JsonPropertyDescription("Label (e.g., 'Z2 Endurance'). Required for leaf blocks, null for sets.")
        String label,

        @JsonPropertyDescription("Short description to add relevant details")
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

    /**
     * Returns true when this element is a set (has children), false when it's a leaf block.
     */
    public boolean isSet() {
        return elements != null && !elements.isEmpty();
    }

    public WorkoutElement updateType(BlockType type) {
        return new WorkoutElement(repetitions, elements, restDurationSeconds, restIntensity,
                type, this.durationSeconds(), this.distanceMeters(), this.label(), this.description,
                this.intensityTarget(), this.intensityStart(), this.intensityEnd(), this.cadenceTarget(),
                this.zoneTarget(), this.zoneLabel());
    }

    public WorkoutElement withElements(List<WorkoutElement> newElements) {
        return new WorkoutElement(repetitions, newElements, restDurationSeconds, restIntensity,
                type, durationSeconds, distanceMeters, label, description,
                intensityTarget, intensityStart, intensityEnd, cadenceTarget,
                zoneTarget, zoneLabel);
    }

    public WorkoutElement withResolvedIntensity(Integer resolvedIntensity, String resolvedZoneLabel) {
        return new WorkoutElement(repetitions, elements, restDurationSeconds, restIntensity,
                type, durationSeconds, distanceMeters, label, description,
                resolvedIntensity, intensityStart, intensityEnd, cadenceTarget, zoneTarget, resolvedZoneLabel);
    }
}
