package com.koval.trainingplannerbackend.training.metrics;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TssCalculatorTest {

    @Nested
    class ComputeTss {

        @Test
        void canonical_oneHourAtThreshold_yields100() {
            double tss = TssCalculator.computeTss(3600, 1.0);
            assertEquals(100.0, tss, 0.001, "1h at IF=1.0 should produce TSS=100");
        }

        @ParameterizedTest(name = "duration={0}s, IF={1} → TSS={2}")
        @CsvSource({
                "3600, 0.75, 56.25",
                "3600, 0.50, 25.0",
                "1800, 1.0,  50.0",
                "7200, 0.80, 128.0",
                "3600, 1.20, 144.0"
        })
        void variousInputs(double durationSec, double intensityFactor, double expectedTss) {
            double tss = TssCalculator.computeTss(durationSec, intensityFactor);
            assertEquals(expectedTss, tss, 0.01,
                    "TSS for %.0fs at IF=%.2f should be %.2f".formatted(durationSec, intensityFactor, expectedTss));
        }

        @Test
        void zeroDuration_yieldsZero() {
            assertEquals(0.0, TssCalculator.computeTss(0, 1.0), 0.001);
        }

        @Test
        void zeroIntensity_yieldsZero() {
            assertEquals(0.0, TssCalculator.computeTss(3600, 0.0), 0.001);
        }
    }

    @Nested
    class ComputeIf {

        @Test
        void canonical_tss100_oneHour_yieldsIF1() {
            double intensityFactor = TssCalculator.computeIf(100.0, 3600);
            assertEquals(1.0, intensityFactor, 0.001, "TSS=100 over 1h should give IF=1.0");
        }

        @Test
        void zeroDuration_yieldsZero() {
            assertEquals(0.0, TssCalculator.computeIf(50.0, 0), 0.001);
        }

        @Test
        void negativeDuration_yieldsZero() {
            assertEquals(0.0, TssCalculator.computeIf(50.0, -100), 0.001);
        }

        @Test
        void zeroTss_yieldsZero() {
            assertEquals(0.0, TssCalculator.computeIf(0.0, 3600), 0.001);
        }
    }

    @Nested
    class RoundTrip {

        @ParameterizedTest(name = "duration={0}s, IF={1}")
        @CsvSource({
                "3600, 0.75",
                "3600, 1.0",
                "1800, 0.85",
                "7200, 0.65"
        })
        void computeIf_of_computeTss_returnsOriginalIF(double durationSec, double originalIf) {
            double tss = TssCalculator.computeTss(durationSec, originalIf);
            double recoveredIf = TssCalculator.computeIf(tss, durationSec);
            assertEquals(originalIf, recoveredIf, 0.001,
                    "Round-trip IF should match original: %.2f".formatted(originalIf));
        }
    }
}
