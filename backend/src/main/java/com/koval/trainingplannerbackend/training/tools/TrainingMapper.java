package com.koval.trainingplannerbackend.training.tools;

import com.koval.trainingplannerbackend.training.model.*;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/** Maps AI-facing {@link TrainingRequest} DTOs to rich {@link Training} entities with sport-specific subclass instantiation. */
@Component
public class TrainingMapper {

    /**
     * Converts a simplified DTO request (from the AI) into a rich Training entity.
     */
    public Training mapToEntity(TrainingRequest request) {
        Training training = createInstance(request.sport());

        // Base fields
        training.setTitle(request.title());
        training.setDescription(request.desc());

        // Groups (default to empty list if null)
        training.setGroupIds(request.groupIds() != null ? request.groupIds() : new ArrayList<>());

        training.setTrainingType(safeValueOf(TrainingType.class, request.type(), TrainingType.MIXED));

        if (request.tss() != null) {
            training.setEstimatedTss(request.tss());
        }

        // Block mapping
        if (request.blocks() != null && !request.blocks().isEmpty()) {
            training.setBlocks(request.blocks().stream()
                    .map(this::mapElement)
                    .toList());
        } else {
            training.setBlocks(new ArrayList<>());
        }

        return training;
    }

    /**
     * Recursively maps a WorkoutElementRequest to a WorkoutElement.
     * Sets map children recursively; leaves enforce distance-xor-duration.
     */
    private WorkoutElement mapElement(WorkoutElementRequest b) {
       return b.isSet() ? set(b) : block(b);
    }

    private WorkoutElement set(WorkoutElementRequest b) {
        List<WorkoutElement> children = b.elements().stream()
                .map(this::mapElement)
                .toList();
        return new WorkoutElement(b.reps(), children, b.restDur(), b.restPct(),
                null, null, null, null, null, null, null, null, null, null, null);
    }

    /**
     * Enforces that each leaf block has exactly one of durationSeconds or distanceMeters.
     */
    private WorkoutElement block(WorkoutElementRequest b) {
        return new WorkoutElement(null, null, null, null,
                b.type(), b.dur(), b.dist(), b.label(), b.desc(), b.pct(), b.pctFrom(), b.pctTo(), b.cad(), b.zone(), null);
    }

    private static <T extends Enum<T>> T safeValueOf(Class<T> enumType, String value, T fallback) {
        if (value == null) return fallback;
        try { return Enum.valueOf(enumType, value.toUpperCase()); }
        catch (IllegalArgumentException e) { return fallback; }
    }

    /**
     * Creates the sport-specific Training subclass based on the sport type string.
     */
    private Training createInstance(String sportType) {
        return switch (SportType.fromString(sportType)) {
            case RUNNING -> new RunningTraining();
            case SWIMMING -> new SwimmingTraining();
            case BRICK -> new BrickTraining();
            case CYCLING -> new CyclingTraining();
        };
    }

}
