package com.koval.trainingplannerbackend.pacing;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class PacingUtilsTest {

    @Nested
    class Interpolate {

        private static final double[] XS = {100, 200, 400, 800};
        private static final double[] YS = {10.0, 20.0, 30.0, 40.0};

        @Test
        void belowRange_returnsFirstValue() {
            assertEquals(10.0, PacingUtils.interpolate(50, XS, YS), 0.001);
        }

        @Test
        void aboveRange_returnsLastValue() {
            assertEquals(40.0, PacingUtils.interpolate(1000, XS, YS), 0.001);
        }

        @Test
        void atKnotPoint_returnsExactValue() {
            assertEquals(20.0, PacingUtils.interpolate(200, XS, YS), 0.001);
        }

        @Test
        void atFirstKnotPoint_returnsExactValue() {
            assertEquals(10.0, PacingUtils.interpolate(100, XS, YS), 0.001);
        }

        @Test
        void atLastKnotPoint_returnsExactValue() {
            assertEquals(40.0, PacingUtils.interpolate(800, XS, YS), 0.001);
        }

        @Test
        void midpointBetweenKnots_linearlyInterpolates() {
            // Midpoint between (100,10) and (200,20) → 15.0
            assertEquals(15.0, PacingUtils.interpolate(150, XS, YS), 0.001);
        }

        @Test
        void quarterPointBetweenKnots() {
            // 25% between (200,20) and (400,30) → x=250 → ratio=0.25 → 22.5
            assertEquals(22.5, PacingUtils.interpolate(250, XS, YS), 0.001);
        }
    }

    @Nested
    class FormatPace {

        @ParameterizedTest(name = "{0}s /{2} → {3}")
        @CsvSource({
                "300, km,  5:00 /km",
                "90,  100m, 1:30 /100m",
                "245, km,  4:05 /km",
                "60,  km,  1:00 /km",
                "0,   km,  0:00 /km"
        })
        void formatsCorrectly(int totalSeconds, String unit, String expected) {
            assertEquals(expected, PacingUtils.formatPace(totalSeconds, unit));
        }
    }

    @Nested
    class FormatDistKm {

        @Test
        void below10km_showsOneDecimal() {
            assertEquals("5.0km", PacingUtils.formatDistKm(5000));
        }

        @Test
        void at10km_showsInteger() {
            assertEquals("10km", PacingUtils.formatDistKm(10000));
        }

        @Test
        void above10km_showsInteger() {
            assertEquals("42km", PacingUtils.formatDistKm(42000));
        }

        @Test
        void smallDistance_showsOneDecimal() {
            assertEquals("1.5km", PacingUtils.formatDistKm(1500));
        }

        @Test
        void nearBoundary_9900m() {
            assertEquals("9.9km", PacingUtils.formatDistKm(9900));
        }
    }

    @Nested
    class FuelSuggestion {

        @ParameterizedTest(name = "{0}/{1}")
        @CsvSource({
                "BIKE, GELS",
                "BIKE, DRINK",
                "BIKE, SOLID",
                "BIKE, MIXED",
                "RUN,  GELS",
                "RUN,  DRINK",
                "RUN,  SOLID",
                "RUN,  MIXED",
                "SWIM, GELS"
        })
        void alwaysReturnsNonNull(String sport, String preference) {
            String result = PacingUtils.fuelSuggestion(sport, preference);
            assertNotNull(result, "Fuel suggestion for %s/%s should not be null".formatted(sport, preference));
        }

        @Test
        void bikeGels_containsGel() {
            String result = PacingUtils.fuelSuggestion("BIKE", "GELS");
            assertEquals("Take 1 energy gel (25g carbs) + 200ml water", result);
        }

        @Test
        void runDrink_containsSportsDrink() {
            String result = PacingUtils.fuelSuggestion("RUN", "DRINK");
            assertEquals("200ml sports drink (20-25g carbs) + water", result);
        }
    }
}
