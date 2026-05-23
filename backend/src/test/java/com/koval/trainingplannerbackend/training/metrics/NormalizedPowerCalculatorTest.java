package com.koval.trainingplannerbackend.training.metrics;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NormalizedPowerCalculatorTest {

    @Test
    void emptyInput_yieldsZero() {
        assertEquals(0.0, NormalizedPowerCalculator.computeNp(List.of()), 0.001);
        assertEquals(0.0, NormalizedPowerCalculator.computeNp(null), 0.001);
    }

    @Test
    void steadyPower_npEqualsAverage() {
        List<Integer> watts = Collections.nCopies(120, 200);
        assertEquals(200.0, NormalizedPowerCalculator.computeNp(watts), 1.0);
    }

    @Test
    void intervalSession_npAboveAverage() {
        // 5×(3min @ 300W + 3min @ 100W): elapsed average = 200W; NP must weight
        // the hard intervals well above that.
        List<Integer> watts = new ArrayList<>(1800);
        for (int set = 0; set < 5; set++) {
            for (int i = 0; i < 180; i++) watts.add(300);
            for (int i = 0; i < 180; i++) watts.add(100);
        }
        double avg = watts.stream().mapToInt(Integer::intValue).average().orElse(0);
        double np = NormalizedPowerCalculator.computeNp(watts);
        assertTrue(np > avg + 20,
                "Interval NP (%.1f) should clearly exceed simple avg (%.1f)".formatted(np, avg));
    }

    @Test
    void shorterThanRollingWindow_stillProducesAnswer() {
        // 10s of data, shorter than the 30-second rolling window. Falls through
        // to the raw values, so NP ≈ avg.
        List<Integer> watts = Collections.nCopies(10, 250);
        assertEquals(250.0, NormalizedPowerCalculator.computeNp(watts), 1.0);
    }

    @Test
    void coastingSamples_lowerNpButNotIgnored() {
        // 50/50 split between 400W and 0W (coasting). The 4th-power kernel
        // suppresses zeros but the long-run mean is still well below 400.
        List<Integer> watts = new ArrayList<>(600);
        for (int i = 0; i < 300; i++) watts.add(400);
        for (int i = 0; i < 300; i++) watts.add(0);
        double np = NormalizedPowerCalculator.computeNp(watts);
        assertTrue(np > 0 && np < 400,
                "NP (%.1f) should be between 0 and 400W".formatted(np));
    }
}
