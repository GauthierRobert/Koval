package com.koval.trainingplannerbackend.mcp;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.koval.trainingplannerbackend.training.model.BlockType;
import com.koval.trainingplannerbackend.training.model.StrokeType;
import com.koval.trainingplannerbackend.training.model.SwimEquipment;
import com.koval.trainingplannerbackend.training.model.TransitionType;

import java.util.List;
import java.util.Set;

/**
 * MCP-facing input DTO for a workout element. Mirrors {@link com.koval.trainingplannerbackend.training.model.WorkoutElement}
 * field-for-field with the same verbose names (no abbreviations) so external clients can construct it directly from
 * the entity's documented schema.
 *
 * <p>An element is either a <b>set</b> (has {@code repetitions} and non-empty {@code elements}) or a
 * <b>leaf block</b> (has {@code type} and {@code durationSeconds} or {@code distanceMeters}). Don't mix.</p>
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record McpWorkoutElementInput(

        // ── SET FIELDS (use when this element is a repeatable group) ──

        @JsonPropertyDescription("Number of repetitions for this set (e.g. 10 for '10x...'). Set-only field.")
        Integer repetitions,

        @JsonPropertyDescription("Child elements (leaf blocks or nested sets). Set-only field.")
        List<McpWorkoutElementInput> elements,

        @JsonPropertyDescription("""
                Rest duration in seconds inserted between each repetition of this set.
                Three semantics, mirroring the manual workout builder UI:
                  • null or 0  → NO REST between reps (the 'No rest' checkbox in the UI).
                  • > 0 with restIntensity null/0  → PASSIVE REST: a full pause of this length, no power/pace target
                    (the 'Passive rest' checkbox in the UI). Materialized as a PAUSE block at 0% intensity.
                  • > 0 with restIntensity > 0  → ACTIVE REST: easy spinning/jogging at restIntensity %
                    of FTP / threshold pace / CSS for this duration.
                Set-only field.""")
        Integer restDurationSeconds,

        @JsonPropertyDescription("""
                Intensity (% of FTP/threshold pace/CSS) during the rest between reps. Only relevant when
                restDurationSeconds > 0. null or 0 means PASSIVE rest (no target, full pause). A typical
                ACTIVE rest value is 60. Set-only field.""")
        Integer restIntensity,

        // ── LEAF FIELDS (use when this element is a single block) ──

        @JsonPropertyDescription("Block type. Required for leaf blocks; null for sets. One of: WARMUP, STEADY, INTERVAL, COOLDOWN, RAMP, FREE, PAUSE, TRANSITION.")
        BlockType type,

        @JsonPropertyDescription("Duration of the leaf block in seconds. Mutually exclusive with distanceMeters.")
        Integer durationSeconds,

        @JsonPropertyDescription("Distance of the leaf block in meters. Mutually exclusive with durationSeconds. Typical for swim/run sets.")
        Integer distanceMeters,

        @JsonPropertyDescription("Human-readable label, e.g. 'Z2 Endurance' or '5min @ FTP'. Required for leaf blocks.")
        String label,

        @JsonPropertyDescription("Short free-text description (≤ 10 words) shown to the athlete.")
        String description,

        @JsonPropertyDescription("Constant intensity target as percent of reference (FTP / threshold pace / CSS). Example: 90 means 90% of FTP. Use for STEADY/INTERVAL.")
        Integer intensityTarget,

        @JsonPropertyDescription("Start intensity % for RAMP blocks. Use together with intensityEnd.")
        Integer intensityStart,

        @JsonPropertyDescription("End intensity % for RAMP blocks. Use together with intensityStart.")
        Integer intensityEnd,

        @JsonPropertyDescription("Cadence target — RPM for cycling/running, SPM for swimming.")
        Integer cadenceTarget,

        @JsonPropertyDescription("Zone label (e.g. 'Z3') for zone-based targeting. Alternative to intensityTarget; resolved server-side using the user's zone system.")
        String zoneTarget,

        @JsonPropertyDescription("Swim stroke: FREESTYLE, BACKSTROKE, BREASTSTROKE, BUTTERFLY, IM, KICK, PULL, DRILL, CHOICE.")
        StrokeType strokeType,

        @JsonPropertyDescription("Swim equipment list: PADDLES, PULL_BUOY, FINS, SNORKEL, BAND, KICKBOARD.")
        Set<SwimEquipment> equipment,

        @JsonPropertyDescription("Send-off interval in seconds for swim sets — swimmer leaves every N seconds; the rest within the interval is sendOffSeconds minus the swim time.")
        Integer sendOffSeconds,

        @JsonPropertyDescription("Transition type for TRANSITION blocks: T1 (swim→bike) or T2 (bike→run).")
        TransitionType transitionType
) {
    public boolean isSet() {
        return elements != null && !elements.isEmpty();
    }
}
