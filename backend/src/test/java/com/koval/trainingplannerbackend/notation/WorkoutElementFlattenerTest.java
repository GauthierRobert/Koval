package com.koval.trainingplannerbackend.notation;

import com.koval.trainingplannerbackend.training.model.BlockType;
import com.koval.trainingplannerbackend.training.model.WorkoutElement;
import com.koval.trainingplannerbackend.training.model.WorkoutElementFlattener;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class WorkoutElementFlattenerTest {

    // ── Helpers ──────────────────────────────────────────────────────────────

    private static WorkoutElement leaf(BlockType type, int durationSeconds, int intensity, String label) {
        return new WorkoutElement(null, null, null, null,
                type, durationSeconds, null, label, null,
                intensity, null, null, null, null, null,
                null, null, null, null);
    }

    private static WorkoutElement set(int reps, List<WorkoutElement> children, Integer restDur) {
        return new WorkoutElement(reps, children, restDur, null,
                null, null, null, null, null, null, null, null, null, null, null,
                null, null, null, null);
    }

    // ── Tests ────────────────────────────────────────────────────────────────

    @Test
    void flatPassthrough() {
        var blocks = List.of(
                leaf(BlockType.WARMUP, 600, 55, "Warm"),
                leaf(BlockType.STEADY, 1200, 90, "SS"),
                leaf(BlockType.COOLDOWN, 300, 45, "Cool")
        );
        List<WorkoutElement> flat = WorkoutElementFlattener.flatten(blocks);
        assertEquals(3, flat.size());
        assertEquals(BlockType.WARMUP, flat.get(0).type());
        assertEquals(BlockType.STEADY, flat.get(1).type());
        assertEquals(BlockType.COOLDOWN, flat.get(2).type());
    }

    @Test
    void singleLevelSet_noRest() {
        // 5x [INTERVAL 30s, STEADY 30s]
        var children = List.of(
                leaf(BlockType.INTERVAL, 30, 120, "Z5"),
                leaf(BlockType.STEADY, 30, 55, "Recovery")
        );
        var blocks = List.of(set(5, children, null));
        List<WorkoutElement> flat = WorkoutElementFlattener.flatten(blocks);
        assertEquals(10, flat.size()); // 5 * 2
        for (int i = 0; i < 10; i += 2) {
            assertEquals(BlockType.INTERVAL, flat.get(i).type());
            assertEquals(BlockType.STEADY, flat.get(i + 1).type());
        }
    }

    @Test
    void singleLevelSet_withRest() {
        // 3x [INTERVAL 180s] R:120s
        var children = List.of(leaf(BlockType.INTERVAL, 180, 110, "Threshold"));
        var blocks = List.of(set(3, children, 120));
        List<WorkoutElement> flat = WorkoutElementFlattener.flatten(blocks);
        // 3 intervals + 2 rest pauses = 5
        assertEquals(5, flat.size());
        assertEquals(BlockType.INTERVAL, flat.get(0).type());
        assertEquals(BlockType.PAUSE, flat.get(1).type());
        assertEquals(120, flat.get(1).durationSeconds());
        assertEquals(BlockType.INTERVAL, flat.get(2).type());
        assertEquals(BlockType.PAUSE, flat.get(3).type());
        assertEquals(BlockType.INTERVAL, flat.get(4).type());
    }

    @Test
    void nestedSets() {
        // 2x( 3x [INTERVAL 30s, STEADY 30s] ) R:120s
        var innerChildren = List.of(
                leaf(BlockType.INTERVAL, 30, 120, "Sprint"),
                leaf(BlockType.STEADY, 30, 55, "Recovery")
        );
        var innerSet = set(3, innerChildren, null);
        var outerSet = set(2, List.of(innerSet), 120);

        List<WorkoutElement> flat = WorkoutElementFlattener.flatten(List.of(outerSet));
        // Inner: 3 * 2 = 6 blocks per rep
        // Outer: 2 * 6 + 1 pause = 13
        assertEquals(13, flat.size());

        // First rep: 6 blocks
        for (int i = 0; i < 6; i += 2) {
            assertEquals(BlockType.INTERVAL, flat.get(i).type());
            assertEquals(BlockType.STEADY, flat.get(i + 1).type());
        }
        // Then rest pause
        assertEquals(BlockType.PAUSE, flat.get(6).type());
        assertEquals(120, flat.get(6).durationSeconds());
        // Second rep: 6 more blocks
        for (int i = 7; i < 13; i += 2) {
            assertEquals(BlockType.INTERVAL, flat.get(i).type());
            assertEquals(BlockType.STEADY, flat.get(i + 1).type());
        }
    }

    @Test
    void emptyInput() {
        assertEquals(List.of(), WorkoutElementFlattener.flatten(null));
        assertEquals(List.of(), WorkoutElementFlattener.flatten(List.of()));
    }

    @Test
    void mixedFlatAndSets() {
        // WARMUP + 5x[INTERVAL, STEADY] + COOLDOWN
        var warmup = leaf(BlockType.WARMUP, 600, 55, "Warm");
        var children = List.of(
                leaf(BlockType.INTERVAL, 30, 120, "Z5"),
                leaf(BlockType.STEADY, 30, 55, "Recovery")
        );
        var setBlock = set(5, children, null);
        var cooldown = leaf(BlockType.COOLDOWN, 300, 45, "Cool");

        List<WorkoutElement> flat = WorkoutElementFlattener.flatten(List.of(warmup, setBlock, cooldown));
        assertEquals(12, flat.size()); // 1 + 10 + 1
        assertEquals(BlockType.WARMUP, flat.get(0).type());
        assertEquals(BlockType.COOLDOWN, flat.get(11).type());
    }

    @Test
    void restIntensityUsedInPause() {
        var children = List.of(leaf(BlockType.INTERVAL, 30, 120, "Sprint"));
        var setWithRestIntensity = new WorkoutElement(2, children, 60, 50,
                null, null, null, null, null, null, null, null, null, null, null,
                null, null, null, null);
        List<WorkoutElement> flat = WorkoutElementFlattener.flatten(List.of(setWithRestIntensity));
        assertEquals(3, flat.size()); // 2 intervals + 1 pause
        assertEquals(BlockType.PAUSE, flat.get(1).type());
        assertEquals(50, flat.get(1).intensityTarget());
    }
}
