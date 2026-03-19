package com.koval.trainingplannerbackend.notation;

import com.koval.trainingplannerbackend.training.model.BlockType;
import com.koval.trainingplannerbackend.training.model.WorkoutElement;
import com.koval.trainingplannerbackend.training.model.WorkoutElementFlattener;
import com.koval.trainingplannerbackend.training.notation.CompactNotationException;
import com.koval.trainingplannerbackend.training.notation.CompactNotationParser;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link CompactNotationParser}.
 * Parser now returns a tree — sets are NOT expanded.
 */
class CompactNotationParserTest {

    // ─────────────────────────────────────────────────────────────────────────
    //  Single standalone blocks
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    void singleDistanceBlock_zonecode_isInterval() {
        List<WorkoutElement> elements = CompactNotationParser.parse("300mFC");

        assertEquals(1, elements.size());
        WorkoutElement b = elements.get(0);
        assertFalse(b.isSet());
        assertEquals(BlockType.INTERVAL, b.type());
        assertEquals(300, b.distanceMeters());
        assertNull(b.durationSeconds());
        assertEquals("FC", b.label());
        assertNull(b.intensityTarget());
    }

    @Test
    void singleDurationBlock_warmup() {
        List<WorkoutElement> elements = CompactNotationParser.parse("10minWARM");

        assertEquals(1, elements.size());
        WorkoutElement b = elements.get(0);
        assertFalse(b.isSet());
        assertEquals(BlockType.WARMUP, b.type());
        assertEquals(600, b.durationSeconds());
        assertNull(b.distanceMeters());
        assertEquals("WARM", b.label());
    }

    @Test
    void singleBlock_cooldown() {
        List<WorkoutElement> elements = CompactNotationParser.parse("200mCOOL");
        assertEquals(1, elements.size());
        assertEquals(BlockType.COOLDOWN, elements.get(0).type());
    }

    @Test
    void singleBlock_pause_code() {
        List<WorkoutElement> elements = CompactNotationParser.parse("60sP");
        assertEquals(1, elements.size());
        assertEquals(BlockType.PAUSE, elements.get(0).type());
        assertEquals(60, elements.get(0).durationSeconds());
    }

    @Test
    void singleBlock_free_code() {
        List<WorkoutElement> elements = CompactNotationParser.parse("30minF");
        assertEquals(1, elements.size());
        assertEquals(BlockType.FREE, elements.get(0).type());
        assertEquals(1800, elements.get(0).durationSeconds());
    }

    @Test
    void singleBlock_steady_unknown_code() {
        List<WorkoutElement> elements = CompactNotationParser.parse("400mE3");
        assertEquals(1, elements.size());
        assertEquals(BlockType.STEADY, elements.get(0).type());
        assertEquals(400, elements.get(0).distanceMeters());
        assertEquals("E3", elements.get(0).label());
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  Direct % syntax
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    void singleBlock_directPercent_isStandalone() {
        List<WorkoutElement> elements = CompactNotationParser.parse("300m80%");
        assertEquals(1, elements.size());
        WorkoutElement b = elements.get(0);
        assertFalse(b.isSet());
        assertEquals(BlockType.STEADY, b.type());
        assertEquals(300, b.distanceMeters());
        assertEquals(80, b.intensityTarget());
    }

    @Test
    void singleBlock_directPercent_label() {
        List<WorkoutElement> elements = CompactNotationParser.parse("200m75%");
        assertEquals(1, elements.size());
        assertEquals(75, elements.get(0).intensityTarget());
        assertEquals("75%", elements.get(0).label());
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  Inline reps — now return set nodes
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    void inlineReps_workOnly_returnsSetNode() {
        List<WorkoutElement> elements = CompactNotationParser.parse("5x100mFC");

        assertEquals(1, elements.size());
        WorkoutElement set = elements.get(0);
        assertTrue(set.isSet());
        assertEquals(5, set.repetitions());
        assertEquals(1, set.elements().size()); // 1 child: work block
        assertEquals(BlockType.INTERVAL, set.elements().get(0).type());
        assertEquals("FC", set.elements().get(0).label());

        // Flatten regression: 5 blocks
        assertEquals(5, WorkoutElementFlattener.flatten(elements).size());
    }

    @Test
    void inlineReps_withActiveRest_returnsSetNode() {
        List<WorkoutElement> elements = CompactNotationParser.parse("5x100mFC/R:100mE3");

        assertEquals(1, elements.size());
        WorkoutElement set = elements.get(0);
        assertTrue(set.isSet());
        assertEquals(5, set.repetitions());
        assertEquals(2, set.elements().size()); // work + rest
        assertEquals(BlockType.INTERVAL, set.elements().get(0).type());
        assertEquals(BlockType.STEADY, set.elements().get(1).type());
        assertEquals("E3", set.elements().get(1).label());

        // Flatten regression: 5 * 2 = 10
        assertEquals(10, WorkoutElementFlattener.flatten(elements).size());
    }

    @Test
    void inlineReps_percentSyntax_flattens() {
        List<WorkoutElement> elements = CompactNotationParser.parse("5x100m80%/R:100m60%");

        assertEquals(1, elements.size());
        assertTrue(elements.get(0).isSet());

        List<WorkoutElement> flat = WorkoutElementFlattener.flatten(elements);
        assertEquals(10, flat.size());
        for (int i = 0; i < 10; i += 2) {
            assertEquals(BlockType.INTERVAL, flat.get(i).type());
            assertEquals(80, flat.get(i).intensityTarget());
            assertEquals(BlockType.STEADY, flat.get(i + 1).type());
            assertEquals(60, flat.get(i + 1).intensityTarget());
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  Outer sets — now return nested set nodes
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    void outerSet_singleRepWithPassiveRest() {
        // 1x(5x300mFC/R:200mE4)/r:2'30
        List<WorkoutElement> elements = CompactNotationParser.parse("1x(5x300mFC/R:200mE4)/r:2'30");

        assertEquals(1, elements.size());
        WorkoutElement outer = elements.get(0);
        assertTrue(outer.isSet());
        assertEquals(1, outer.repetitions());
        assertEquals(150, outer.restDurationSeconds()); // 2'30 = 150s

        // Inner: 1 set node (5x...)
        assertEquals(1, outer.elements().size());
        WorkoutElement inner = outer.elements().get(0);
        assertTrue(inner.isSet());
        assertEquals(5, inner.repetitions());

        // Flatten: 1 rep → no inter-rep rest pause. 5 × 2 children = 10 blocks.
        assertEquals(10, WorkoutElementFlattener.flatten(elements).size());
    }

    @Test
    void outerSet_twoRepsWithPassiveRest() {
        // 2x(4x300mFC/R:200mE4)/r:2'
        List<WorkoutElement> elements = CompactNotationParser.parse("2x(4x300mFC/R:200mE4)/r:2'");

        assertEquals(1, elements.size());
        WorkoutElement outer = elements.get(0);
        assertTrue(outer.isSet());
        assertEquals(2, outer.repetitions());
        assertEquals(120, outer.restDurationSeconds()); // 2' = 120s

        // Flatten regression: 2 × (4 × 2) + 1 pause = 17
        List<WorkoutElement> flat = WorkoutElementFlattener.flatten(elements);
        assertEquals(17, flat.size());
    }

    @Test
    void outerSet_noPassiveRest() {
        // 2x(3x200mFC)
        List<WorkoutElement> elements = CompactNotationParser.parse("2x(3x200mFC)");

        assertEquals(1, elements.size());
        WorkoutElement outer = elements.get(0);
        assertTrue(outer.isSet());
        assertEquals(2, outer.repetitions());
        assertNull(outer.restDurationSeconds());

        // Flatten: 2 * 3 = 6
        assertEquals(6, WorkoutElementFlattener.flatten(elements).size());
    }

    @Test
    void outerSet_passiveRest_doubleQuote_seconds() {
        // 3x(2x100mFC)/r:30"
        List<WorkoutElement> elements = CompactNotationParser.parse("3x(2x100mFC)/r:30\"");

        assertEquals(1, elements.size());
        WorkoutElement outer = elements.get(0);
        assertTrue(outer.isSet());
        assertEquals(3, outer.repetitions());
        assertEquals(30, outer.restDurationSeconds());

        // Flatten: 3 × 2 + 2 pauses = 8
        List<WorkoutElement> flat = WorkoutElementFlattener.flatten(elements);
        assertEquals(8, flat.size());
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  Multi-section (joined by +)
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    void fullUserExample_threeSection_treeShape() {
        // 1x(5x300mFC/R:200mE4)/r:2'30 + 2x(4x300mFC/R:200mE4)/r:2' + 600mFC
        String notation = "1x(5x300mFC/R:200mE4)/r:2'30 + 2x(4x300mFC/R:200mE4)/r:2' + 600mFC";
        List<WorkoutElement> elements = CompactNotationParser.parse(notation);

        assertEquals(3, elements.size()); // 3 top-level elements
        assertTrue(elements.get(0).isSet());   // outer set
        assertTrue(elements.get(1).isSet());   // outer set
        assertFalse(elements.get(2).isSet());  // leaf

        // Flatten regression: old flat count was 30
        // Section 1: 1 × (5×2) + 1 pause = 11
        // Section 2: 2 × (4×2) + 1 pause = 17
        // Section 3: 1
        // Total: 29 (was 30 because old code added pause after EVERY rep including last)
        // Actually let's recalculate:
        // Section 1: outer(1 rep, rest=150s): 1×[inner(5×2=10)] + 0 pauses (1 rep = no inter-rep rest) = 10
        //   But there IS a rest of 150s and 1 rep. No rest after last rep. So 10 + 0 = 10.
        //   Wait — the original test said 11. The original code added pause after EVERY rep (even last).
        //   Our flattener does NOT add pause after last rep. So:
        //   Section 1: 1 rep → 10 blocks, no inter-rep pause = 10
        //   Section 2: 2 reps → 2×8 + 1 pause = 17
        //   Section 3: 1
        //   Total: 28
        // The old code had a bug — it added rest after every rep including last. Our flattener is correct.
        List<WorkoutElement> flat = WorkoutElementFlattener.flatten(elements);
        assertEquals(28, flat.size());

        // Last block is 600m INTERVAL (FC = high-effort standalone)
        WorkoutElement last = flat.get(flat.size() - 1);
        assertEquals(BlockType.INTERVAL, last.type());
        assertEquals(600, last.distanceMeters());
    }

    @Test
    void multipleSections_warmupIntervalsCooldown() {
        // 400mWARM + 5x100mFC/R:100mE3 + 200mCOOL
        List<WorkoutElement> elements = CompactNotationParser.parse("400mWARM + 5x100mFC/R:100mE3 + 200mCOOL");

        assertEquals(3, elements.size()); // WARMUP leaf + set + COOLDOWN leaf
        assertFalse(elements.get(0).isSet());
        assertTrue(elements.get(1).isSet());
        assertFalse(elements.get(2).isSet());

        assertEquals(BlockType.WARMUP, elements.get(0).type());
        assertEquals(BlockType.COOLDOWN, elements.get(2).type());
        assertEquals(5, elements.get(1).repetitions());

        // Flatten regression: 1 + 10 + 1 = 12
        assertEquals(12, WorkoutElementFlattener.flatten(elements).size());
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  Units
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    void unit_km_convertedToMeters() {
        List<WorkoutElement> elements = CompactNotationParser.parse("1kmWARM");
        assertEquals(1000, elements.get(0).distanceMeters());
        assertEquals(BlockType.WARMUP, elements.get(0).type());
    }

    @Test
    void unit_sec() {
        List<WorkoutElement> elements = CompactNotationParser.parse("90secE2");
        assertEquals(90, elements.get(0).durationSeconds());
    }

    @Test
    void unit_h() {
        List<WorkoutElement> elements = CompactNotationParser.parse("1hE2");
        assertEquals(3600, elements.get(0).durationSeconds());
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
        assertThrows(CompactNotationException.class, () -> CompactNotationParser.parse("300FC"));
    }

    @Test
    void invalidInput_unclosedParen_throws() {
        assertThrows(CompactNotationException.class, () -> CompactNotationParser.parse("2x(3x100mFC"));
    }

    @Test
    void invalidInput_trailingInput_throws() {
        assertThrows(CompactNotationException.class, () -> CompactNotationParser.parse("300mFC + "));
    }
}
