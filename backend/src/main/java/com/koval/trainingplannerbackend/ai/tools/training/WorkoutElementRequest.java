package com.koval.trainingplannerbackend.ai.tools.training;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.koval.trainingplannerbackend.training.model.BlockType;
import com.koval.trainingplannerbackend.training.model.StrokeType;
import com.koval.trainingplannerbackend.training.model.SwimEquipment;
import com.koval.trainingplannerbackend.training.model.TransitionType;

import java.util.List;
import java.util.Set;

/** AI-facing compact DTO for a workout element (set or leaf block). Mirrors {@link com.koval.trainingplannerbackend.training.model.WorkoutElement} with abbreviated field names. */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record WorkoutElementRequest(
        @JsonPropertyDescription("Repetitions for a set (e.g. 10)")
        Integer reps,
        @JsonPropertyDescription("Children elements (leaf blocks or nested sets)")
        List<WorkoutElementRequest> elements,
        @JsonPropertyDescription("Rest duration (sec) between reps. null/0 = no rest at all; >0 = insert a rest block (passive if restPct is null/0, active at restPct% otherwise).")
        Integer restDur,
        @JsonPropertyDescription("Rest intensity % between reps (only used when restDur > 0). null/0 = passive rest (full pause). Typical active value: 60.")
        Integer restPct,
        @JsonPropertyDescription("WARMUP|INTERVAL|STEADY|COOLDOWN|RAMP|FREE|PAUSE|TRANSITION")
        BlockType type,
        @JsonPropertyDescription("Duration in seconds (mutually exclusive with dist)")
        Integer dur,
        @JsonPropertyDescription("Distance in meters (mutually exclusive with dur)")
        Integer dist,
        @JsonPropertyDescription("Zone label, e.g. 'Z2' or 'Z4 VO2max'")
        String label,
        @JsonPropertyDescription("Short description (max 10 words)")
        String desc,
        @JsonPropertyDescription("Intensity % of reference (e.g. 90 = 90% FTP)")
        Integer pct,
        @JsonPropertyDescription("Ramp start % (RAMP only, use with pctTo)")
        Integer pctFrom,
        @JsonPropertyDescription("Ramp end % (RAMP only, use with pctFrom)")
        Integer pctTo,
        @JsonPropertyDescription("Cadence target (RPM or SPM)")
        Integer cad,
        @JsonPropertyDescription("Zone label for zone-based targeting (e.g. 'Z3')")
        String zone,
        @JsonPropertyDescription("Swim stroke: FREESTYLE|BACKSTROKE|BREASTSTROKE|BUTTERFLY|IM|KICK|PULL|DRILL|CHOICE")
        StrokeType stroke,
        @JsonPropertyDescription("Swim equipment: PADDLES, PULL_BUOY, FINS, SNORKEL, BAND, KICKBOARD")
        Set<SwimEquipment> equip,
        @JsonPropertyDescription("Send-off interval (sec). Leave every N sec; rest = sendOff - swim time.")
        Integer sendOff,
        @JsonPropertyDescription("T1 (swim-to-bike) or T2 (bike-to-run). Only for TRANSITION blocks.")
        TransitionType transition
) {
    public boolean isSet() {
        return elements != null && !elements.isEmpty();
    }
}
