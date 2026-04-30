package com.koval.trainingplannerbackend.training.metrics;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NormalizedSpeedCalculatorTest {

    @Nested
    class Ngp {

        @Test
        void emptyInput_yieldsZero() {
            assertEquals(0.0, NormalizedSpeedCalculator.computeNgp(List.of(), List.of()), 0.001);
            assertEquals(0.0, NormalizedSpeedCalculator.computeNgp(null, null), 0.001);
        }

        @Test
        void steadySpeedFlatTerrain_ngpEqualsAverage() {
            List<Double> speed = constantList(120, 4.0); // 4 m/s, 2 minutes
            List<Double> alt = constantList(120, 100.0); // flat
            double ngp = NormalizedSpeedCalculator.computeNgp(speed, alt);
            assertEquals(4.0, ngp, 0.01);
        }

        @Test
        void uphill_ngpAboveActualSpeed() {
            // 120s @ 3 m/s climbing 0.15 m/s (+5% grade): NGP > 3 m/s
            List<Double> speed = constantList(120, 3.0);
            List<Double> alt = new ArrayList<>(120);
            for (int i = 0; i < 120; i++) alt.add(100 + 0.15 * i);
            double ngp = NormalizedSpeedCalculator.computeNgp(speed, alt);
            assertTrue(ngp > 3.0, "uphill NGP should exceed flat-equivalent actual speed; got " + ngp);
        }

        @Test
        void downhill_ngpBelowActualSpeed() {
            // 120s @ 3 m/s descending 0.15 m/s (-5% grade): NGP < 3 m/s
            List<Double> speed = constantList(120, 3.0);
            List<Double> alt = new ArrayList<>(120);
            for (int i = 0; i < 120; i++) alt.add(100 - 0.15 * i);
            double ngp = NormalizedSpeedCalculator.computeNgp(speed, alt);
            assertTrue(ngp < 3.0, "downhill NGP should fall below actual speed; got " + ngp);
        }

        @Test
        void intervalPattern_ngpAboveSimpleAverage() {
            // 5×(60s @ 5 m/s + 60s @ 1 m/s): avg = 3 m/s; NGP weights hard intervals
            List<Double> speed = new ArrayList<>(600);
            List<Double> alt = constantList(600, 50.0);
            for (int set = 0; set < 5; set++) {
                for (int i = 0; i < 60; i++) speed.add(5.0);
                for (int i = 0; i < 60; i++) speed.add(1.0);
            }
            double ngp = NormalizedSpeedCalculator.computeNgp(speed, alt);
            assertTrue(ngp > 3.0,
                    "interval NGP must exceed simple avg of 3.0; got " + ngp);
        }

        @Test
        void shorterThanRollingWindow_stillProducesAnswer() {
            // 10s of data — shorter than the 30s rolling window. Should fall through to raw values.
            List<Double> speed = constantList(10, 4.0);
            List<Double> alt = constantList(10, 100.0);
            double ngp = NormalizedSpeedCalculator.computeNgp(speed, alt);
            assertEquals(4.0, ngp, 0.01);
        }

        @Test
        void noisyFlatAltitude_doesNotInflateNgp() {
            // 1h flat run at 3 m/s with ±5m altitude noise (typical GPS/baro RMS).
            // The naive per-second derivative would turn this into apparent ±30% grades
            // and inflate NGP because Minetti's polynomial is asymmetric. A correct
            // implementation should keep NGP close to the actual 3 m/s.
            int n = 3600;
            List<Double> speed = constantList(n, 3.0);
            List<Double> alt = new ArrayList<>(n);
            java.util.Random rng = new java.util.Random(42);
            for (int i = 0; i < n; i++) alt.add(100 + (rng.nextDouble() - 0.5) * 10.0); // ±5m
            double ngp = NormalizedSpeedCalculator.computeNgp(speed, alt);
            assertTrue(ngp < 3.15,
                    "flat-run NGP must not be inflated by altitude noise; got " + ngp);
        }
    }

    @Nested
    class Nss {

        @Test
        void emptyInput_yieldsZero() {
            assertEquals(0.0, NormalizedSpeedCalculator.computeNss(List.of()), 0.001);
            assertEquals(0.0, NormalizedSpeedCalculator.computeNss(null), 0.001);
        }

        @Test
        void steadySpeed_nssEqualsAverage() {
            List<Double> speed = constantList(120, 1.25); // 80s/100m pace
            assertEquals(1.25, NormalizedSpeedCalculator.computeNss(speed), 0.01);
        }

        @Test
        void poolSetWithRests_nssAboveAverage() {
            // 5×(50s swim @ 1.5 m/s + 30s rest @ 0.0 m/s): avg ≈ 0.94, NSS weights hard portions
            List<Double> speed = new ArrayList<>(400);
            for (int set = 0; set < 5; set++) {
                for (int i = 0; i < 50; i++) speed.add(1.5);
                for (int i = 0; i < 30; i++) speed.add(0.0);
            }
            double avg = speed.stream().mapToDouble(Double::doubleValue).average().orElse(0);
            double nss = NormalizedSpeedCalculator.computeNss(speed);
            assertTrue(nss > avg,
                    "NSS (%.3f) must exceed simple avg (%.3f) for interval set".formatted(nss, avg));
        }
    }

    private static List<Double> constantList(int n, double value) {
        return new ArrayList<>(Collections.nCopies(n, value));
    }
}
