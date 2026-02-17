package com.koval.trainingplannerbackend.training.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record WorkoutBlock(

        @JsonPropertyDescription("Type: WARMUP, ACTIVE, REST, INTERVAL, COOLDOWN, FREE, PAUSE")
        @JsonProperty(required = true)
        BlockType type,

        @JsonPropertyDescription("Duration in seconds (ONLY if distance is not set)")
        Integer durationSeconds,

        @JsonPropertyDescription("Distance in meters (ONLY if duration is not set, e.g. for Swimming/Running)")
        Integer distanceMeters,

        @JsonPropertyDescription("Label (e.g. 'Hard', 'Recovery'). Add Zone Label (e.g. 'Z2', 'Z4', or Custom name). " +
                                 "Use Custom Zone System if zoneSystemId is not null. Else use Default.")
        @JsonProperty(required = true)
        String label,

        @JsonPropertyDescription("Zone Label: Custom zone System Id, if specified by the Coach. " +
                                 "Optional. " +
                                 "Replace Default reference Target value if Set")
        String zoneSystemId,

        // --- UNIFIED INTENSITY FIELDS (PERCENTAGES) ---
        @JsonPropertyDescription("Target Intensity as % of Reference (FTP for Bike, Threshold Pace for Run, CSS for Swim). " +
                                 "Example: 90 = 90%.")
        Integer intensityTarget,

        @JsonPropertyDescription("Start Intensity % (For Ramps). " +
                                 "Reference: FTP/Threshold/CSS.")
        Integer intensityStart,

        @JsonPropertyDescription("End Intensity % (For Ramps). " +
                                 "Reference: FTP/Threshold/CSS.")
        Integer intensityEnd,

        // --- CADENCE ---
        @JsonPropertyDescription("Cadence target (RPM for Bike/Run, SPM for Swim). " +
                                 "Optional.")
        Integer cadenceTarget
) {}