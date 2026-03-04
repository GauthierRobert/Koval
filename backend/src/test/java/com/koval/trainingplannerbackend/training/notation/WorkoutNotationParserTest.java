package com.koval.trainingplannerbackend.training.notation;

import com.koval.trainingplannerbackend.training.model.BlockType;
import com.koval.trainingplannerbackend.training.model.WorkoutBlock;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class WorkoutNotationParserTest {

    // ── Primitives ────────────────────────────────────────────────────────────

    @Test
    void steadyBlock() {
        var blocks = parse("10min85%");
        assertEquals(1, blocks.size());
        var b = blocks.get(0);
        assertEquals(BlockType.STEADY, b.type());
        assertEquals(600, b.durationSeconds());
        assertEquals(85, b.intensityTarget());
    }

    @Test
    void rampBlock() {
        var blocks = parse("10min60>90%");
        assertEquals(1, blocks.size());
        var b = blocks.get(0);
        assertEquals(BlockType.RAMP, b.type());
        assertEquals(600, b.durationSeconds());
        assertEquals(60, b.intensityStart());
        assertEquals(90, b.intensityEnd());
        assertNull(b.intensityTarget());
    }

    @Test
    void pauseBlock() {
        var blocks = parse("90sP");
        assertEquals(1, blocks.size());
        var b = blocks.get(0);
        assertEquals(BlockType.PAUSE, b.type());
        assertEquals(90, b.durationSeconds());
        assertNull(b.intensityTarget());
    }

    @Test
    void warmupBlock() {
        var blocks = parse("15minW60%");
        assertEquals(1, blocks.size());
        var b = blocks.get(0);
        assertEquals(BlockType.WARMUP, b.type());
        assertEquals(900, b.durationSeconds());
        assertEquals(60, b.intensityTarget());
    }

    @Test
    void warmupNoIntensity() {
        var blocks = parse("10minW");
        assertEquals(1, blocks.size());
        assertEquals(BlockType.WARMUP, blocks.get(0).type());
        assertNull(blocks.get(0).intensityTarget());
    }

    @Test
    void cooldownBlock() {
        var blocks = parse("10minC55%");
        assertEquals(1, blocks.size());
        var b = blocks.get(0);
        assertEquals(BlockType.COOLDOWN, b.type());
        assertEquals(600, b.durationSeconds());
        assertEquals(55, b.intensityTarget());
    }

    @Test
    void freeBlock() {
        var blocks = parse("20minF");
        assertEquals(1, blocks.size());
        assertEquals(BlockType.FREE, blocks.get(0).type());
    }

    @Test
    void blockWithCadence() {
        var blocks = parse("10min85%@90");
        assertEquals(1, blocks.size());
        assertEquals(85,  blocks.get(0).intensityTarget());
        assertEquals(90,  blocks.get(0).cadenceTarget());
    }

    @Test
    void decimalDuration() {
        var blocks = parse("1.5min100%");
        assertEquals(90, blocks.get(0).durationSeconds()); // 1.5 × 60 = 90
    }

    @Test
    void hoursUnit() {
        var blocks = parse("1h60%");
        assertEquals(3600, blocks.get(0).durationSeconds());
    }

    @Test
    void secondsUnit() {
        var blocks = parse("30s120%");
        assertEquals(30, blocks.get(0).durationSeconds());
    }

    @Test
    void distanceBlock() {
        var blocks = parse("2km85%");
        assertEquals(1, blocks.size());
        assertNull(blocks.get(0).durationSeconds());
        assertEquals(2000, blocks.get(0).distanceMeters());
    }

    @Test
    void noModifierIsFree() {
        var blocks = parse("10min");
        assertEquals(1, blocks.size());
        assertEquals(BlockType.FREE, blocks.get(0).type());
    }

    // ── Sequences ─────────────────────────────────────────────────────────────

    @Test
    void twoBlocks() {
        var blocks = parse("10min85%-5minP");
        assertEquals(2, blocks.size());
        assertEquals(BlockType.STEADY, blocks.get(0).type());
        assertEquals(BlockType.PAUSE,  blocks.get(1).type());
    }

    @Test
    void fullWorkout() {
        // 1 warmup + 5×(on+rest) + 1 cooldown = 12 blocks
        var blocks = parse("10minW60%-5*(3min105%-2minP)-10minC55%");
        assertEquals(12, blocks.size());
        assertEquals(BlockType.WARMUP,   blocks.get(0).type());
        assertEquals(BlockType.INTERVAL, blocks.get(1).type());
        assertEquals(BlockType.PAUSE,    blocks.get(2).type());
        assertEquals(BlockType.INTERVAL, blocks.get(9).type());
        assertEquals(BlockType.COOLDOWN, blocks.get(11).type());
    }

    // ── Interval expansion ────────────────────────────────────────────────────

    @Test
    void singleRepExpansion() {
        var blocks = parse("1*(1min100%-30sP)");
        assertEquals(2, blocks.size());
        assertEquals(BlockType.INTERVAL, blocks.get(0).type());
        assertEquals(BlockType.PAUSE,    blocks.get(1).type());
    }

    @Test
    void tenRepExpansion() {
        // 10*(1min100%-1minP) → 20 blocks
        var blocks = parse("10*(1min100%-1minP)");
        assertEquals(20, blocks.size());
        for (int i = 0; i < 20; i += 2) {
            assertEquals(BlockType.INTERVAL, blocks.get(i).type());
            assertEquals(100, blocks.get(i).intensityTarget());
            assertEquals(BlockType.PAUSE, blocks.get(i + 1).type());
        }
    }

    @Test
    void intervalInsideTopLevelBecomesINTERVAL() {
        // N% inside *(…) must be INTERVAL, not STEADY
        var blocks = parse("3*(2min90%)");
        assertTrue(blocks.stream().allMatch(b -> b.type() == BlockType.INTERVAL));
    }

    @Test
    void nestedIntervals() {
        // 2*(3*(30s120%-30sP)-1minP) → 2 × (3×[INT,PAUSE] + PAUSE) = 2×7 = 14 blocks
        var blocks = parse("2*(3*(30s120%-30sP)-1minP)");
        assertEquals(14, blocks.size());
    }

    @Test
    void rampInsideInterval() {
        // Ramp keeps RAMP type even inside *(…)
        var blocks = parse("2*(5min60>90%-2minP)");
        assertEquals(4, blocks.size());
        assertEquals(BlockType.RAMP,  blocks.get(0).type());
        assertEquals(BlockType.PAUSE, blocks.get(1).type());
    }

    @Test
    void threeInnerBlocks() {
        // 2*(1min120%-1min80%-1minP) → 6 blocks
        var blocks = parse("2*(1min120%-1min80%-1minP)");
        assertEquals(6, blocks.size());
    }

    // ── Duration totals ────────────────────────────────────────────────────────

    @Test
    void durationAddsUp() {
        // 10*(1min100%-1minP) = 10×2min = 20min = 1200s
        var blocks = parse("10*(1min100%-1minP)");
        int total = blocks.stream()
                .mapToInt(b -> b.durationSeconds() != null ? b.durationSeconds() : 0)
                .sum();
        assertEquals(1200, total);
    }

    // ── Error cases ────────────────────────────────────────────────────────────

    @Test
    void emptyNotationThrows() {
        assertThrows(WorkoutNotationException.class, () -> parse(""));
    }

    @Test
    void unknownModifierThrows() {
        assertThrows(WorkoutNotationException.class, () -> parse("10minX"));
    }

    @Test
    void missingUnitThrows() {
        // "10*" without parentheses and then invalid
        assertThrows(WorkoutNotationException.class, () -> parse("10*5min"));
    }

    // ── Helper ────────────────────────────────────────────────────────────────

    private List<WorkoutBlock> parse(String notation) {
        return WorkoutNotationParser.parse(notation);
    }
}
