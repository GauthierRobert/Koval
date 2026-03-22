package com.koval.trainingplannerbackend.pacing;

import com.koval.trainingplannerbackend.pacing.BikePacingService.BikeAero;
import com.koval.trainingplannerbackend.pacing.BikePacingService.BikeEnvironment;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pure unit tests for {@link BikeSpeedService}.
 * Verifies physics-based speed calculations across bike types and gradients.
 */
class BikeSpeedServiceTest {

    private BikeSpeedService service;

    // Bike aero profiles (must match BikePacingService.getBikeAero)
    private static final BikeAero TT = new BikeAero(0.24, 0.004);
    private static final BikeAero ROAD_AERO = new BikeAero(0.28, 0.005);
    private static final BikeAero ROAD = new BikeAero(0.32, 0.005);

    private static final int RIDER_WEIGHT = 75;
    private static final double NO_WIND = 0.0;
    private static final double SEA_LEVEL = 0.0;

    private BikeEnvironment env(BikeAero aero) {
        return new BikeEnvironment(RIDER_WEIGHT, NO_WIND, aero);
    }

    @BeforeEach
    void setUp() {
        service = new BikeSpeedService();
    }

    @Nested
    class TtAlwaysFasterThanRoadAero {

        @ParameterizedTest(name = "gradient={0}%")
        @ValueSource(doubles = {-0.05, -0.03, -0.01, 0.0, 0.01, 0.03, 0.05, 0.08, 0.10, 0.15})
        void ttFasterThanRoadAero_atAllGradients(double gradient) {
            double power = 250;
            double ttSpeed = service.steadyStateSpeed(power, gradient, SEA_LEVEL, env(TT));
            double roadAeroSpeed = service.steadyStateSpeed(power, gradient, SEA_LEVEL, env(ROAD_AERO));

            assertTrue(ttSpeed > roadAeroSpeed,
                    "TT (%.2f m/s) should be faster than ROAD_AERO (%.2f m/s) at gradient %.1f%%"
                            .formatted(ttSpeed, roadAeroSpeed, gradient * 100));
        }

        @ParameterizedTest(name = "gradient={0}%")
        @ValueSource(doubles = {-0.05, -0.03, -0.01, 0.0, 0.01, 0.03, 0.05, 0.08, 0.10, 0.15})
        void ttFasterThanRoad_atAllGradients(double gradient) {
            double power = 250;
            double ttSpeed = service.steadyStateSpeed(power, gradient, SEA_LEVEL, env(TT));
            double roadSpeed = service.steadyStateSpeed(power, gradient, SEA_LEVEL, env(ROAD));

            assertTrue(ttSpeed > roadSpeed,
                    "TT (%.2f m/s) should be faster than ROAD (%.2f m/s) at gradient %.1f%%"
                            .formatted(ttSpeed, roadSpeed, gradient * 100));
        }

        @ParameterizedTest(name = "gradient={0}%")
        @ValueSource(doubles = {-0.05, -0.03, -0.01, 0.0, 0.01, 0.03, 0.05, 0.08, 0.10, 0.15})
        void roadAeroFasterThanRoad_atAllGradients(double gradient) {
            double power = 250;
            double roadAeroSpeed = service.steadyStateSpeed(power, gradient, SEA_LEVEL, env(ROAD_AERO));
            double roadSpeed = service.steadyStateSpeed(power, gradient, SEA_LEVEL, env(ROAD));

            assertTrue(roadAeroSpeed > roadSpeed,
                    "ROAD_AERO (%.2f m/s) should be faster than ROAD (%.2f m/s) at gradient %.1f%%"
                            .formatted(roadAeroSpeed, roadSpeed, gradient * 100));
        }
    }

    @Nested
    class TtAlwaysFaster_DifferentPowers {

        @ParameterizedTest(name = "power={0}W")
        @ValueSource(doubles = {150, 200, 250, 300, 350, 400})
        void ttFasterThanRoad_flat_allPowers(double power) {
            double ttSpeed = service.steadyStateSpeed(power, 0.0, SEA_LEVEL, env(TT));
            double roadSpeed = service.steadyStateSpeed(power, 0.0, SEA_LEVEL, env(ROAD));

            assertTrue(ttSpeed > roadSpeed,
                    "TT (%.2f m/s) should be faster than ROAD (%.2f m/s) at %dW"
                            .formatted(ttSpeed, roadSpeed, (int) power));
        }
    }

    @Nested
    class GradientEffects {

        @Test
        void speedDecreasesWithIncreasingGradient() {
            double power = 250;
            double flatSpeed = service.steadyStateSpeed(power, 0.0, SEA_LEVEL, env(ROAD_AERO));
            double uphillSpeed = service.steadyStateSpeed(power, 0.05, SEA_LEVEL, env(ROAD_AERO));
            double steepSpeed = service.steadyStateSpeed(power, 0.10, SEA_LEVEL, env(ROAD_AERO));

            assertTrue(flatSpeed > uphillSpeed, "Flat should be faster than 5%% uphill");
            assertTrue(uphillSpeed > steepSpeed, "5%% uphill should be faster than 10%% uphill");
        }

        @Test
        void downhillFasterThanFlat() {
            double power = 250;
            double downhillSpeed = service.steadyStateSpeed(power, -0.03, SEA_LEVEL, env(ROAD_AERO));
            double flatSpeed = service.steadyStateSpeed(power, 0.0, SEA_LEVEL, env(ROAD_AERO));

            assertTrue(downhillSpeed > flatSpeed, "Downhill should be faster than flat");
        }

        @Test
        void ttAdvantageGreaterOnFlat_thanSteepClimb() {
            double power = 250;
            double ttFlat = service.steadyStateSpeed(power, 0.0, SEA_LEVEL, env(TT));
            double roadFlat = service.steadyStateSpeed(power, 0.0, SEA_LEVEL, env(ROAD));
            double flatAdvantage = ttFlat - roadFlat;

            double ttClimb = service.steadyStateSpeed(power, 0.10, SEA_LEVEL, env(TT));
            double roadClimb = service.steadyStateSpeed(power, 0.10, SEA_LEVEL, env(ROAD));
            double climbAdvantage = ttClimb - roadClimb;

            assertTrue(flatAdvantage > climbAdvantage,
                    "TT aero advantage should be larger on flat (%.3f m/s) than on 10%% climb (%.3f m/s)"
                            .formatted(flatAdvantage, climbAdvantage));
        }
    }

    @Nested
    class SteadyStateSpeedSanity {

        @Test
        void speedIsPositive() {
            double speed = service.steadyStateSpeed(200, 0.0, SEA_LEVEL, env(ROAD_AERO));
            assertTrue(speed > 0, "Speed must be positive");
        }

        @Test
        void speedIsCappedAtMax() {
            // Very high power on steep downhill should still be capped
            double speed = service.steadyStateSpeed(1000, -0.10, SEA_LEVEL, env(TT));
            assertTrue(speed <= 22.0, "Speed should be capped at MAX_SPEED_MS (22 m/s)");
        }

        @Test
        void higherPowerMeansFasterSpeed() {
            double low = service.steadyStateSpeed(150, 0.0, SEA_LEVEL, env(ROAD_AERO));
            double high = service.steadyStateSpeed(300, 0.0, SEA_LEVEL, env(ROAD_AERO));
            assertTrue(high > low, "300W should produce higher speed than 150W");
        }

        @Test
        void higherAltitudeMeansSlightlyFaster_onFlat() {
            // Lower air density at altitude → less aero drag → slightly faster on flat
            double seaLevel = service.steadyStateSpeed(250, 0.0, 0, env(ROAD_AERO));
            double altitude = service.steadyStateSpeed(250, 0.0, 2000, env(ROAD_AERO));
            assertTrue(altitude > seaLevel,
                    "Higher altitude (less air density) should be slightly faster on flat");
        }
    }

    @Nested
    class ComputeSegmentSpeed {

        @Test
        void shortSegmentUsesAverageOfEntryAndSteady() {
            double power = 250;
            double entrySpeed = 12.0;
            double segmentLength = 30.0; // < 50m threshold

            BikeSpeedService.SpeedResult result = service.computeSegmentSpeed(
                    power, 0.0, SEA_LEVEL, entrySpeed, segmentLength, env(ROAD_AERO));

            double steadyState = service.steadyStateSpeed(power, 0.0, SEA_LEVEL, env(ROAD_AERO));
            double expectedAvg = (entrySpeed + steadyState) / 2.0;

            assertEquals(expectedAvg, result.effectiveSpeed(), 0.01,
                    "Short segment should use simple average of entry and steady-state speed");
            assertEquals(steadyState, result.exitSpeed(), 0.01,
                    "Exit speed of short segment should equal steady-state speed");
        }

        @Test
        void longSegmentConvergesToSteadyState() {
            double power = 250;
            double entrySpeed = 5.0; // slow entry
            double segmentLength = 5000.0; // 5km — very long

            BikeSpeedService.SpeedResult result = service.computeSegmentSpeed(
                    power, 0.0, SEA_LEVEL, entrySpeed, segmentLength, env(ROAD_AERO));

            double steadyState = service.steadyStateSpeed(power, 0.0, SEA_LEVEL, env(ROAD_AERO));

            assertEquals(steadyState, result.exitSpeed(), 0.01,
                    "Exit speed after long segment should converge to steady-state");
            assertEquals(steadyState, result.effectiveSpeed(), 0.2,
                    "Effective speed over long segment should be close to steady-state");
        }

        @Test
        void ttFasterThanRoad_segmentSpeed() {
            double power = 250;
            double entrySpeed = 10.0;
            double segmentLength = 500.0;

            BikeSpeedService.SpeedResult ttResult = service.computeSegmentSpeed(
                    power, 0.0, SEA_LEVEL, entrySpeed, segmentLength, env(TT));
            BikeSpeedService.SpeedResult roadResult = service.computeSegmentSpeed(
                    power, 0.0, SEA_LEVEL, entrySpeed, segmentLength, env(ROAD));

            assertTrue(ttResult.effectiveSpeed() > roadResult.effectiveSpeed(),
                    "TT effective speed should be faster than ROAD on segment");
            assertTrue(ttResult.exitSpeed() > roadResult.exitSpeed(),
                    "TT exit speed should be faster than ROAD on segment");
        }
    }
}
