package com.koval.trainingplannerbackend.training.model;

import java.util.ArrayList;
import java.util.List;

/**
 * Flattens a tree of {@link WorkoutElement} into a sequential list of leaf blocks.
 * Sets are expanded: children repeated N times with optional PAUSE blocks between reps.
 */
public final class WorkoutElementFlattener {

    private WorkoutElementFlattener() {}

    /**
     * Flattens a list of elements (which may contain nested sets) into a flat list of leaf blocks.
     */
    public static List<WorkoutElement> flatten(List<WorkoutElement> elements) {
        if (elements == null || elements.isEmpty()) {
            return List.of();
        }
        List<WorkoutElement> result = new ArrayList<>();
        for (WorkoutElement element : elements) {
            flattenElement(element, result);
        }
        return result;
    }

    private static void flattenElement(WorkoutElement element, List<WorkoutElement> result) {
        if (!element.isSet()) {
            // Leaf block — add directly
            result.add(element);
            return;
        }

        // Set — flatten children, repeat N times, insert PAUSE between reps
        int reps = element.repetitions() != null ? element.repetitions() : 1;
        List<WorkoutElement> flatChildren = flatten(element.elements());

        for (int i = 0; i < reps; i++) {
            result.addAll(flatChildren);
            // Insert PAUSE rest between reps (not after the last one)
            // restIntensity sets the intensity target of the pause block
            if (i < reps - 1 && element.restDurationSeconds() != null && element.restDurationSeconds() > 0) {
                int restIntensity = element.restIntensity() != null ? element.restIntensity() : 0;
                result.add(new WorkoutElement(
                        null, null, null, null,
                        BlockType.PAUSE, element.restDurationSeconds(), null,
                        "Rest", null, restIntensity, null, null, null, null, null));
            }
        }
    }
}
