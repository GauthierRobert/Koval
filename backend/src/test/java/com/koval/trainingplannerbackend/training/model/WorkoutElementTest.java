package com.koval.trainingplannerbackend.training.model;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class WorkoutElementTest {

    private static WorkoutElement leaf(BlockType type, int durationSeconds, Integer intensity, String label) {
        return new WorkoutElement(null, null, null, null,
                type, durationSeconds, null, label, null,
                intensity, null, null, null, null, null,
                null, null, null, null);
    }

    private static WorkoutElement set(int reps, List<WorkoutElement> children) {
        return new WorkoutElement(reps, children, null, null,
                null, null, null, null, null, null, null, null, null, null, null,
                null, null, null, null);
    }

    @Nested
    class IsSet {

        @Test
        void leafBlock_isNotSet() {
            assertFalse(leaf(BlockType.STEADY, 600, 80, "Z3").isSet());
        }

        @Test
        void emptyElementsList_isNotSet() {
            WorkoutElement e = set(2, List.of());
            assertFalse(e.isSet());
        }

        @Test
        void nullElementsList_isNotSet() {
            WorkoutElement e = new WorkoutElement(2, null, 30, null,
                    null, null, null, null, null, null, null, null, null, null, null,
                    null, null, null, null);
            assertFalse(e.isSet());
        }

        @Test
        void elementsWithChildren_isSet() {
            WorkoutElement child = leaf(BlockType.INTERVAL, 30, 120, "Z5");
            WorkoutElement parent = set(5, List.of(child));
            assertTrue(parent.isSet());
        }
    }

    @Nested
    class UpdateType {

        @Test
        void replacesType_preservesAllOtherFields() {
            WorkoutElement original = new WorkoutElement(null, null, null, null,
                    BlockType.STEADY, 600, 1000, "Original Label", "desc",
                    85, 80, 90, 95, "Z3", "Z3 - Tempo",
                    StrokeType.FREESTYLE, null, 110, null);

            WorkoutElement updated = original.updateType(BlockType.INTERVAL);

            assertEquals(BlockType.INTERVAL, updated.type());
            assertEquals(original.durationSeconds(), updated.durationSeconds());
            assertEquals(original.distanceMeters(), updated.distanceMeters());
            assertEquals(original.label(), updated.label());
            assertEquals(original.description(), updated.description());
            assertEquals(original.intensityTarget(), updated.intensityTarget());
            assertEquals(original.intensityStart(), updated.intensityStart());
            assertEquals(original.intensityEnd(), updated.intensityEnd());
            assertEquals(original.cadenceTarget(), updated.cadenceTarget());
            assertEquals(original.zoneTarget(), updated.zoneTarget());
            assertEquals(original.zoneLabel(), updated.zoneLabel());
            assertEquals(original.strokeType(), updated.strokeType());
            assertEquals(original.sendOffSeconds(), updated.sendOffSeconds());
        }

        @Test
        void doesNotMutateOriginal() {
            WorkoutElement original = leaf(BlockType.STEADY, 600, 80, "Z3");
            WorkoutElement updated = original.updateType(BlockType.WARMUP);

            assertEquals(BlockType.STEADY, original.type(), "original should not be mutated");
            assertEquals(BlockType.WARMUP, updated.type());
        }
    }

    @Nested
    class WithElements {

        @Test
        void replacesChildren_preservesSetMetadata() {
            WorkoutElement original = new WorkoutElement(5, List.of(leaf(BlockType.INTERVAL, 30, 120, "Z5")), 60, 40,
                    null, null, null, "set label", "set desc", null, null, null, null, null, null,
                    null, null, null, null);

            List<WorkoutElement> newChildren = List.of(leaf(BlockType.STEADY, 60, 70, "Z2"));
            WorkoutElement updated = original.withElements(newChildren);

            assertEquals(newChildren, updated.elements());
            assertEquals(5, updated.repetitions());
            assertEquals(60, updated.restDurationSeconds());
            assertEquals(40, updated.restIntensity());
            assertEquals("set label", updated.label());
            assertEquals("set desc", updated.description());
        }
    }

    @Nested
    class WithResolvedIntensity {

        @Test
        void replacesIntensityTargetAndZoneLabel() {
            WorkoutElement original = new WorkoutElement(null, null, null, null,
                    BlockType.STEADY, 600, null, "block", null,
                    null, null, null, null,
                    "Z3", null, null, null, null, null);

            WorkoutElement resolved = original.withResolvedIntensity(85, "Z3 - Tempo (75-90%)");

            assertEquals(85, resolved.intensityTarget());
            assertEquals("Z3 - Tempo (75-90%)", resolved.zoneLabel());
            assertEquals("Z3", resolved.zoneTarget(), "zoneTarget remains as the symbolic name");
        }

        @Test
        void doesNotMutateOriginal() {
            WorkoutElement original = leaf(BlockType.STEADY, 600, null, "Z3");
            original.withResolvedIntensity(80, "Resolved");
            assertNull(original.intensityTarget());
        }
    }
}
