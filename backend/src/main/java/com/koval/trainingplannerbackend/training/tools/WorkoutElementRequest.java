package com.koval.trainingplannerbackend.training.tools;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.koval.trainingplannerbackend.training.model.BlockType;

import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record WorkoutElementRequest(
        @JsonPropertyDescription("SETS : Number of repetitions for a set (e.g. 10 for '10×...')")
        Integer reps,
        @JsonPropertyDescription("SETS : Children elements for a set (leaf blocks or nested sets)")
        List<WorkoutElementRequest> elements,
        @JsonPropertyDescription("SETS : Passive rest duration (seconds) between repetitions")
        Integer restDur,
        @JsonPropertyDescription("SETS : Intensity % during rest between reps (default ~60)")
        Integer restPct,
        @JsonPropertyDescription("LEAF FIELDS : WARMUP|INTERVAL|STEADY|COOLDOWN|RAMP|FREE|PAUSE — leaf block type")
        BlockType type,
        @JsonPropertyDescription("LEAF FIELDS : Duration in seconds — exactly one of dur or dist, never both")
        Integer dur,
        @JsonPropertyDescription("LEAF FIELDS : Distance in meters — exactly one of dur or dist, never both")
        Integer dist,
        @JsonPropertyDescription("LEAF FIELDS : Zone label, e.g. 'Z2' or 'Z4 VO2max'")
        String label,
        @JsonPropertyDescription("LEAF FIELDS : Short description of the block (max 10 words)")
        String desc,
        @JsonPropertyDescription("LEAF FIELDS : Intensity as % of reference (e.g. 90 = 90% FTP). For STEADY/INTERVAL/WARMUP/COOLDOWN")
        Integer pct,
        @JsonPropertyDescription("LEAF FIELDS : Ramp start % — use with pctTo for RAMP blocks only")
        Integer pctFrom,
        @JsonPropertyDescription("LEAF FIELDS : Ramp end % — use with pctFrom for RAMP blocks only")
        Integer pctTo,
        @JsonPropertyDescription("LEAF FIELDS : Cadence target (RPM for cycling, SPM for running)")
        Integer cad,
        @JsonPropertyDescription("LEAF FIELDS : Zone label (e.g. 'Z3') — use instead of pct for zone-based targeting")
        String zone
) {
    public boolean isSet() {
        return elements != null && !elements.isEmpty();
    }
}
