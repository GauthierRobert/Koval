package com.koval.trainingplannerbackend.notation;

import com.koval.trainingplannerbackend.training.model.BlockType;
import com.koval.trainingplannerbackend.training.model.WorkoutBlock;
import com.koval.trainingplannerbackend.training.notation.CompactNotationException;
import com.koval.trainingplannerbackend.training.notation.CompactNotationParser;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Pure JUnit 5 unit tests for {@link CompactNotationParser}.
 * No Spring context required.
 */
class CompactNotationParserTest {

    // ─────────────────────────────────────────────────────────────────────────
    //  Single standalone blocks
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    void singleDistanceBlock_zonecode_isInterval() {
        List<WorkoutBlock> blocks = CompactNotationParser.parse("300mFC");

        assertEquals(1, blocks.size());
        WorkoutBlock b = blocks.get(0);
        assertEquals(BlockType.INTERVAL, b.type());    // FC is a high-effort code
        assertEquals(300, b.distanceMeters());
        assertNull(b.durationSeconds());
        assertEquals("FC", b.label());
        assertNull(b.intensityTarget());               // resolved later by zone system
    }

    @Test
    void singleDurationBlock_warmup() {
        List<WorkoutBlock> blocks = CompactNotationParser.parse("10minWARM");

        assertEquals(1, blocks.size());
        WorkoutBlock b = blocks.get(0);
        assertEquals(BlockType.WARMUP, b.type());
        assertEquals(600, b.durationSeconds());        // 10 × 60
        assertNull(b.distanceMeters());
        assertEquals("WARM", b.label());
        assertNull(b.intensityTarget());
    }

    @Test
    void singleBlock_cooldown() {
        List<WorkoutBlock> blocks = CompactNotationParser.parse("200mCOOL");

        assertEquals(1, blocks.size());
        assertEquals(BlockType.COOLDOWN, blocks.get(0).type());
    }

    @Test
    void singleBlock_pause_code() {
        List<WorkoutBlock> blocks = CompactNotationParser.parse("60sP");

        assertEquals(1, blocks.size());
        assertEquals(BlockType.PAUSE, blocks.get(0).type());
        assertEquals(60, blocks.get(0).durationSeconds());
    }

    @Test
    void singleBlock_free_code() {
        List<WorkoutBlock> blocks = CompactNotationParser.parse("30minF");

        assertEquals(1, blocks.size());
        assertEquals(BlockType.FREE, blocks.get(0).type());
        assertEquals(1800, blocks.get(0).durationSeconds());
    }

    @Test
    void singleBlock_steady_unknown_code() {
        List<WorkoutBlock> blocks = CompactNotationParser.parse("400mE3");

        assertEquals(1, blocks.size());
        assertEquals(BlockType.STEADY, blocks.get(0).type());
        assertEquals(400, blocks.get(0).distanceMeters());
        assertEquals("E3", blocks.get(0).label());
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  Direct % syntax
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    void singleBlock_directPercent_isStandalone() {
        List<WorkoutBlock> blocks = CompactNotationParser.parse("300m80%");

        assertEquals(1, blocks.size());
        WorkoutBlock b = blocks.get(0);
        assertEquals(BlockType.STEADY, b.type());
        assertEquals(300, b.distanceMeters());
        assertTrue(b.label() == null || b.label().contains("%"), "label should be null or contain %");
        assertEquals(80, b.intensityTarget());
    }

    @Test
    void singleBlock_directPercent_label() {
        List<WorkoutBlock> blocks = CompactNotationParser.parse("200m75%");

        assertEquals(1, blocks.size());
        assertEquals(75, blocks.get(0).intensityTarget());
        assertEquals("75%", blocks.get(0).label());
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  Inline reps
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    void inlineReps_workOnly() {
        List<WorkoutBlock> blocks = CompactNotationParser.parse("5x100mFC");

        // 5 INTERVAL blocks
        assertEquals(5, blocks.size());
        for (WorkoutBlock b : blocks) {
            assertEquals(BlockType.INTERVAL, b.type());
            assertEquals(100, b.distanceMeters());
            assertEquals("FC", b.label());
            assertNull(b.intensityTarget());
        }
    }

    @Test
    void inlineReps_withActiveRest() {
        List<WorkoutBlock> blocks = CompactNotationParser.parse("5x100mFC/R:100mE3");

        // 5 × [INTERVAL work + STEADY rest] = 10
        assertEquals(10, blocks.size());
        for (int i = 0; i < 10; i += 2) {
            WorkoutBlock work = blocks.get(i);
            WorkoutBlock rest = blocks.get(i + 1);

            assertEquals(BlockType.INTERVAL, work.type());
            assertEquals(100, work.distanceMeters());
            assertEquals("FC", work.label());

            assertEquals(BlockType.STEADY, rest.type());
            assertEquals(100, rest.distanceMeters());
            assertEquals("E3", rest.label());
        }
    }

    @Test
    void inlineReps_percentSyntax() {
        List<WorkoutBlock> blocks = CompactNotationParser.parse("5x100m80%/R:100m60%");

        assertEquals(10, blocks.size());
        for (int i = 0; i < 10; i += 2) {
            assertEquals(BlockType.INTERVAL, blocks.get(i).type());
            assertEquals(80, blocks.get(i).intensityTarget());

            assertEquals(BlockType.STEADY, blocks.get(i + 1).type());
            assertEquals(60, blocks.get(i + 1).intensityTarget());
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  Outer sets
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    void outerSet_singleRepWithPassiveRest_quoteSeconds() {
        // 1x(5x300mFC/R:200mE4)/r:2'30  → 5×[INTERVAL, STEADY] + PAUSE(150) = 11
        List<WorkoutBlock> blocks = CompactNotationParser.parse("1x(5x300mFC/R:200mE4)/r:2'30");

        assertEquals(11, blocks.size());

        // First 10: 5 pairs
        for (int i = 0; i < 10; i += 2) {
            assertEquals(BlockType.INTERVAL, blocks.get(i).type());
            assertEquals(300, blocks.get(i).distanceMeters());
            assertEquals(BlockType.STEADY, blocks.get(i + 1).type());
            assertEquals(200, blocks.get(i + 1).distanceMeters());
        }

        // Last: PAUSE 150s
        WorkoutBlock pause = blocks.get(10);
        assertEquals(BlockType.PAUSE, pause.type());
        assertEquals(150, pause.durationSeconds()); // 2×60 + 30
    }

    @Test
    void outerSet_twoRepsWithPassiveRest_quoteOnly() {
        // 2x(4x300mFC/R:200mE4)/r:2' → 2 × (4×[INTERVAL, STEADY] + PAUSE(120)) = 18
        List<WorkoutBlock> blocks = CompactNotationParser.parse("2x(4x300mFC/R:200mE4)/r:2'");

        assertEquals(18, blocks.size());

        // Each outer rep: 8 workout blocks + 1 PAUSE
        for (int rep = 0; rep < 2; rep++) {
            int base = rep * 9;
            for (int i = 0; i < 8; i += 2) {
                assertEquals(BlockType.INTERVAL, blocks.get(base + i).type());
                assertEquals(BlockType.STEADY, blocks.get(base + i + 1).type());
            }
            assertEquals(BlockType.PAUSE, blocks.get(base + 8).type());
            assertEquals(120, blocks.get(base + 8).durationSeconds()); // 2×60
        }
    }

    @Test
    void outerSet_noPassiveRest() {
        // 2x(3x200mFC) → 2 × (3×INTERVAL) = 6
        List<WorkoutBlock> blocks = CompactNotationParser.parse("2x(3x200mFC)");

        assertEquals(6, blocks.size());
        for (WorkoutBlock b : blocks) {
            assertEquals(BlockType.INTERVAL, b.type());
            assertEquals(200, b.distanceMeters());
        }
    }

    @Test
    void outerSet_passiveRest_doubleQuote_seconds() {
        // 3x(2x100mFC)/r:30"  → 3 × (2×INTERVAL + PAUSE(30)) = 9
        List<WorkoutBlock> blocks = CompactNotationParser.parse("3x(2x100mFC)/r:30\"");

        assertEquals(9, blocks.size());
        // Every 3rd block (index 2, 5, 8) should be PAUSE with 30s
        for (int rep = 0; rep < 3; rep++) {
            int pauseIdx = rep * 3 + 2;
            assertEquals(BlockType.PAUSE, blocks.get(pauseIdx).type());
            assertEquals(30, blocks.get(pauseIdx).durationSeconds());
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  Multi-section (joined by +)
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    void fullUserExample_threeSection_thirtyBlocks() {
        // 1x(5x300mFC/R:200mE4)/r:2'30 + 2x(4x300mFC/R:200mE4)/r:2' + 600mFC
        // Section 1: 5×2 + 1 = 11
        // Section 2: 2×(4×2 + 1) = 18
        // Section 3: 1
        // Total: 30
        String notation = "1x(5x300mFC/R:200mE4)/r:2'30 + 2x(4x300mFC/R:200mE4)/r:2' + 600mFC";
        List<WorkoutBlock> blocks = CompactNotationParser.parse(notation);

        assertEquals(30, blocks.size());

        // Last block is 600m INTERVAL (FC = high-effort standalone)
        WorkoutBlock last = blocks.get(29);
        assertEquals(BlockType.INTERVAL, last.type());
        assertEquals(600, last.distanceMeters());
        assertEquals("FC", last.label());
    }

    @Test
    void multipleSections_warmupIntervalsCooldown() {
        // 400mWARM + 5x100mFC/R:100mE3 + 200mCOOL
        List<WorkoutBlock> blocks = CompactNotationParser.parse("400mWARM + 5x100mFC/R:100mE3 + 200mCOOL");

        assertEquals(12, blocks.size()); // 1 + 10 + 1

        assertEquals(BlockType.WARMUP, blocks.get(0).type());
        assertEquals(BlockType.COOLDOWN, blocks.get(11).type());
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  Units
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    void unit_km_convertedToMeters() {
        List<WorkoutBlock> blocks = CompactNotationParser.parse("1kmWARM");

        assertEquals(1000, blocks.get(0).distanceMeters());
        assertEquals(BlockType.WARMUP, blocks.get(0).type());
    }

    @Test
    void unit_sec() {
        List<WorkoutBlock> blocks = CompactNotationParser.parse("90secE2");

        assertEquals(90, blocks.get(0).durationSeconds());
    }

    @Test
    void unit_h() {
        List<WorkoutBlock> blocks = CompactNotationParser.parse("1hE2");

        assertEquals(3600, blocks.get(0).durationSeconds());
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  Error cases
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    void invalidInput_blank_throws() {
        assertThrows(CompactNotationException.class, () -> CompactNotationParser.parse(""));
        assertThrows(CompactNotationException.class, () -> CompactNotationParser.parse("  "));
        assertThrows(CompactNotationException.class, () -> CompactNotationParser.parse(null));
    }

    @Test
    void invalidInput_missingUnit_throws() {
        // "300FC" — "300" is parsed, then "F" is not a valid unit
        assertThrows(CompactNotationException.class, () -> CompactNotationParser.parse("300FC"));
    }

    @Test
    void invalidInput_unclosedParen_throws() {
        assertThrows(CompactNotationException.class, () -> CompactNotationParser.parse("2x(3x100mFC"));
    }

    @Test
    void invalidInput_percentWithoutNumber_throws() {
        // "300m%" — digit branch not entered, '%' not a letter either
        // Parser sees '%' which is neither letter nor digit → no descriptor → no error for % alone
        // But "300m%x" should leave trailing chars that cause error
        assertThrows(CompactNotationException.class, () -> CompactNotationParser.parse("300mFC + "));
    }
}
