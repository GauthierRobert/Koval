package com.koval.trainingplannerbackend.mcp;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;

import java.util.List;

/**
 * MCP-facing input DTO for creating or updating a training. Uses verbose names that match
 * {@link com.koval.trainingplannerbackend.training.model.Training} so external clients can build it
 * directly from the entity's documented schema, without the abbreviated naming used by the AI-internal
 * {@code TrainingRequest}.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record McpTrainingInput(

        @JsonPropertyDescription("Sport discipline: CYCLING, RUNNING, SWIMMING, or BRICK. Drives which subclass of Training is created and how intensities are interpreted (FTP / threshold pace / CSS).")
        String sportType,

        @JsonPropertyDescription("Workout title. Required.")
        String title,

        @JsonProperty(required = false)
        @JsonPropertyDescription("Free-text description / goal of the workout.")
        String description,

        @JsonProperty(required = false)
        @JsonPropertyDescription("Training type: VO2MAX, THRESHOLD, SWEET_SPOT, ENDURANCE, SPRINT, RECOVERY, MIXED, TEST.")
        String trainingType,

        @JsonProperty(required = false)
        @JsonPropertyDescription("Estimated TSS for the whole workout. Optional — server will recompute if omitted.")
        Integer estimatedTss,

        @JsonProperty(required = false)
        @JsonPropertyDescription("Zone system ID to use when resolving zoneTarget on blocks. Optional; defaults to the user's default for this sport.")
        String zoneSystemId,

        @JsonPropertyDescription("Ordered top-level workout elements (leaf blocks or sets).")
        List<McpWorkoutElementInput> blocks,

        @JsonProperty(required = false)
        @JsonPropertyDescription("Coach-only: list of group IDs the training should be shared with.")
        List<String> groupIds
) {}
