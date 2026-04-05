package com.koval.trainingplannerbackend.pacing;

import com.koval.trainingplannerbackend.pacing.dto.AthleteProfile;
import com.koval.trainingplannerbackend.pacing.dto.PacingSummary;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class SwimPacingServiceTest {

    private SwimPacingService service;

    @BeforeEach
    void setUp() {
        service = new SwimPacingService();
    }

    private AthleteProfile profile(Integer swimDist, Integer css, Integer targetPace) {
        return new AthleteProfile(
                250, 75, 300, css,
                0.5, "MIXED",
                null, null,
                swimDist, targetPace, "ROAD_AERO"
        );
    }

    @Nested
    class ReturnsNull {

        @Test
        void whenSwimDistanceIsNull() {
            assertNull(service.buildSummary(profile(null, 90, null)));
        }

        @Test
        void whenSwimDistanceIsZero() {
            assertNull(service.buildSummary(profile(0, 90, null)));
        }

        @Test
        void whenSwimDistanceIsNegative() {
            assertNull(service.buildSummary(profile(-100, 90, null)));
        }

        @Test
        void whenCssIsNull() {
            assertNull(service.buildSummary(profile(1500, null, null)));
        }

        @Test
        void whenCssIsZero() {
            assertNull(service.buildSummary(profile(1500, 0, null)));
        }
    }

    @Nested
    class WithExplicitTargetPace {

        @Test
        void usesExplicitPaceDirectly() {
            PacingSummary summary = service.buildSummary(profile(1500, 90, 95));

            assertNotNull(summary);
            assertEquals(1500, summary.totalDistance());
            assertNull(summary.targetBasis(), "Explicit target should have null targetBasis");
            assertNull(summary.computedTarget(), "Explicit target should have null computedTarget");
        }

        @Test
        void estimatedTimeMatchesPaceTimesDistance() {
            int pace = 100; // 100 sec per 100m
            int distance = 1500;
            PacingSummary summary = service.buildSummary(profile(distance, 90, pace));

            double expectedTime = (distance / 100.0) * pace; // 1500.0
            assertEquals(expectedTime, summary.estimatedTotalTime(), 0.1);
        }
    }

    @Nested
    class WithCssBasedTarget {

        @Test
        void computesTargetFromCss() {
            PacingSummary summary = service.buildSummary(profile(1500, 90, null));

            assertNotNull(summary);
            assertNotNull(summary.targetBasis(), "CSS-based should have targetBasis");
            assertNotNull(summary.computedTarget(), "CSS-based should have computedTarget");
        }

        @Test
        void longerDistanceProducesSlowerPace() {
            PacingSummary short750 = service.buildSummary(profile(750, 90, null));
            PacingSummary long3800 = service.buildSummary(profile(3800, 90, null));

            assertNotNull(short750);
            assertNotNull(long3800);
            assertTrue(long3800.computedTarget() > short750.computedTarget(),
                    "3800m pace (%d) should be slower than 750m pace (%d)"
                            .formatted(long3800.computedTarget(), short750.computedTarget()));
        }

        @Test
        void paceFormatIncludesUnit() {
            PacingSummary summary = service.buildSummary(profile(1500, 90, null));
            assertTrue(summary.averagePace().contains("/100m"),
                    "Pace format should contain /100m: " + summary.averagePace());
        }

        @Test
        void totalDistanceMatchesInput() {
            PacingSummary summary = service.buildSummary(profile(3800, 90, null));
            assertEquals(3800, summary.totalDistance());
        }

        @Test
        void nutritionPlanIsHydrationAdvice() {
            PacingSummary summary = service.buildSummary(profile(1500, 90, null));
            assertEquals("Hydrate well before start", summary.nutritionPlan());
        }

        @Test
        void caloriesIsZero() {
            PacingSummary summary = service.buildSummary(profile(1500, 90, null));
            assertEquals(0, summary.totalCalories());
        }
    }
}
