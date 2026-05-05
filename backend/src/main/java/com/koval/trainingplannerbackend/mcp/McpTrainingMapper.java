package com.koval.trainingplannerbackend.mcp;

import com.koval.trainingplannerbackend.training.model.BrickTraining;
import com.koval.trainingplannerbackend.training.model.CyclingTraining;
import com.koval.trainingplannerbackend.training.model.RunningTraining;
import com.koval.trainingplannerbackend.training.model.SportType;
import com.koval.trainingplannerbackend.training.model.SwimmingTraining;
import com.koval.trainingplannerbackend.training.model.Training;
import com.koval.trainingplannerbackend.training.model.TrainingType;
import com.koval.trainingplannerbackend.training.model.WorkoutElement;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Maps the verbose MCP-facing {@link McpTrainingInput} / {@link McpWorkoutElementInput} into rich
 * {@link Training} / {@link WorkoutElement} entities. Kept separate from the AI-internal mapper
 * (which works on abbreviated DTOs) so each surface has its own clear contract.
 */
@Component
public class McpTrainingMapper {

    public Training mapToEntity(McpTrainingInput request) {
        Training training = createInstance(request.sportType());

        training.setTitle(request.title());
        training.setDescription(request.description());
        training.setGroupIds(Optional.ofNullable(request.groupIds()).orElseGet(ArrayList::new));
        training.setTrainingType(safeValueOf(TrainingType.class, request.trainingType(), TrainingType.MIXED));
        training.setZoneSystemId(request.zoneSystemId());

        if (request.estimatedTss() != null) {
            training.setEstimatedTss(request.estimatedTss());
        }

        if (request.blocks() != null && !request.blocks().isEmpty()) {
            training.setBlocks(request.blocks().stream().map(this::mapElement).toList());
        } else {
            training.setBlocks(new ArrayList<>());
        }

        return training;
    }

    private WorkoutElement mapElement(McpWorkoutElementInput in) {
        return in.isSet() ? mapSet(in) : mapLeaf(in);
    }

    private WorkoutElement mapSet(McpWorkoutElementInput in) {
        List<WorkoutElement> children = in.elements().stream().map(this::mapElement).toList();
        return new WorkoutElement(
                in.repetitions(), children, in.restDurationSeconds(), in.restIntensity(),
                null, null, null, null, null, null, null, null, null, null, null,
                null, null, null, null);
    }

    private WorkoutElement mapLeaf(McpWorkoutElementInput in) {
        return new WorkoutElement(
                null, null, null, null,
                in.type(), in.durationSeconds(), in.distanceMeters(), in.label(), in.description(),
                in.intensityTarget(), in.intensityStart(), in.intensityEnd(),
                in.cadenceTarget(), in.zoneTarget(), in.zoneTarget(),
                in.strokeType(), in.equipment(), in.sendOffSeconds(), in.transitionType());
    }

    private static <T extends Enum<T>> T safeValueOf(Class<T> enumType, String value, T fallback) {
        if (value == null) return fallback;
        try { return Enum.valueOf(enumType, value.toUpperCase()); }
        catch (IllegalArgumentException e) { return fallback; }
    }

    private Training createInstance(String sportType) {
        return switch (SportType.fromString(sportType)) {
            case RUNNING -> new RunningTraining();
            case SWIMMING -> new SwimmingTraining();
            case BRICK -> new BrickTraining();
            case CYCLING -> new CyclingTraining();
        };
    }

    /** Validates an MCP training input. Returns null if valid, otherwise a human-readable error message. */
    public static String validate(McpTrainingInput request) {
        if (request == null) {
            return "Error: training input is required.";
        }
        if (request.title() == null || request.title().isBlank()) {
            return "Error: title is required and cannot be blank.";
        }
        if (request.blocks() == null || request.blocks().isEmpty()) {
            return "Error: blocks list is required and cannot be empty.";
        }
        return validateElements(request.blocks(), "block");
    }

    private static final int MAX_INTENSITY_PERCENT = 250;

    private static String validateElements(List<McpWorkoutElementInput> elements, String path) {
        for (int i = 0; i < elements.size(); i++) {
            McpWorkoutElementInput element = elements.get(i);
            String prefix = path + "[" + i + "]";
            String error = element.isSet() ? validateSet(element, prefix) : validateLeaf(element, prefix);
            if (error != null) return error;
        }
        return null;
    }

    private static String validateSet(McpWorkoutElementInput element, String prefix) {
        if (element.repetitions() == null || element.repetitions() < 1) {
            return "Error: " + prefix + " is a set but has invalid repetitions=" + element.repetitions() + " (must be >= 1).";
        }
        if (element.restDurationSeconds() != null && element.restDurationSeconds() < 0) {
            return "Error: " + prefix + " has invalid restDurationSeconds=" + element.restDurationSeconds() + " (must be >= 0; use 0 or null for no rest).";
        }
        if (element.restIntensity() != null
                && (element.restIntensity() < 0 || element.restIntensity() > MAX_INTENSITY_PERCENT)) {
            return "Error: " + prefix + " has out-of-range restIntensity=" + element.restIntensity()
                    + " (expected 0-" + MAX_INTENSITY_PERCENT + "; use 0 or null for passive rest).";
        }
        return validateElements(element.elements(), prefix + ".elements");
    }

    private static String validateLeaf(McpWorkoutElementInput e, String prefix) {
        if (e.type() == null) {
            return "Error: " + prefix + " is missing required field 'type'.";
        }
        if (e.intensityTarget() != null && (e.intensityTarget() < 0 || e.intensityTarget() > MAX_INTENSITY_PERCENT)) {
            return "Error: " + prefix + " has out-of-range intensityTarget=" + e.intensityTarget()
                    + " (expected 0-" + MAX_INTENSITY_PERCENT + ").";
        }
        if (e.intensityStart() != null && (e.intensityStart() < 0 || e.intensityStart() > MAX_INTENSITY_PERCENT)) {
            return "Error: " + prefix + " has out-of-range intensityStart=" + e.intensityStart()
                    + " (expected 0-" + MAX_INTENSITY_PERCENT + ").";
        }
        if (e.intensityEnd() != null && (e.intensityEnd() < 0 || e.intensityEnd() > MAX_INTENSITY_PERCENT)) {
            return "Error: " + prefix + " has out-of-range intensityEnd=" + e.intensityEnd()
                    + " (expected 0-" + MAX_INTENSITY_PERCENT + ").";
        }
        if (e.durationSeconds() != null && e.durationSeconds() <= 0) {
            return "Error: " + prefix + " has invalid durationSeconds=" + e.durationSeconds() + " (must be > 0).";
        }
        if (e.distanceMeters() != null && e.distanceMeters() <= 0) {
            return "Error: " + prefix + " has invalid distanceMeters=" + e.distanceMeters() + " (must be > 0).";
        }
        return null;
    }
}
