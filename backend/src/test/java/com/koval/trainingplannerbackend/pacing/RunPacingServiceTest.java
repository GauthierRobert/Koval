package com.koval.trainingplannerbackend.pacing;

import com.koval.trainingplannerbackend.pacing.dto.AthleteProfile;
import com.koval.trainingplannerbackend.pacing.dto.PacingSegment;
import com.koval.trainingplannerbackend.pacing.gpx.CourseSegment;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class RunPacingServiceTest {

    private RunPacingService service;

    @BeforeEach
    void setUp() {
        service = new RunPacingService();
    }

    private AthleteProfile profile(Integer thresholdPace, Integer targetPace, Double temp, Double fatigue) {
        return new AthleteProfile(
                250, 75, thresholdPace, 90,
                fatigue, "MIXED",
                null, targetPace,
                null, null, "ROAD_AERO"
        );
    }

    private CourseSegment flat(double startM, double endM) {
        return new CourseSegment(startM, endM, 0.0, 0, 0, 100, 100);
    }

    private CourseSegment uphill(double startM, double endM, double gradient) {
        double len = endM - startM;
        double gain = len * gradient;
        return new CourseSegment(startM, endM, gradient, gain, 0, 100, 100 + gain);
    }

    private CourseSegment downhill(double startM, double endM, double gradient) {
        double len = endM - startM;
        double loss = len * Math.abs(gradient);
        return new CourseSegment(startM, endM, gradient, 0, loss, 100, 100 - loss);
    }

    @Nested
    class ComputeEffectiveTarget {

        @Test
        void explicitTargetPace_usedDirectly() {
            var course = List.of(flat(0, 10000));
            var target = service.computeEffectiveTarget(course, profile(300, 280, 20.0, 0.5));

            assertEquals(280, target.pace());
            assertNull(target.basis());
            assertNull(target.computed());
        }

        @Test
        void thresholdPace_computesTarget() {
            var course = List.of(flat(0, 10000));
            var target = service.computeEffectiveTarget(course, profile(300, null, 20.0, 0.5));

            assertNotNull(target.basis());
            assertNotNull(target.computed());
            assertTrue(target.pace() > 0);
        }

        @Test
        void noThresholdOrTarget_fallbackTo300() {
            var course = List.of(flat(0, 10000));
            var target = service.computeEffectiveTarget(course, profile(null, null, 20.0, 0.5));

            assertEquals(300, target.pace());
        }

        @Test
        void longerDistance_slowerPace() {
            var short5k = List.of(flat(0, 5000));
            var long42k = List.of(flat(0, 42195));

            var targetShort = service.computeEffectiveTarget(short5k, profile(300, null, 20.0, 0.5));
            var targetLong = service.computeEffectiveTarget(long42k, profile(300, null, 20.0, 0.5));

            assertTrue(targetLong.pace() > targetShort.pace(),
                    "Marathon pace (%d) should be slower than 5k pace (%d)"
                            .formatted(targetLong.pace(), targetShort.pace()));
        }

        @Test
        void highElevationGain_slowerPace() {
            // Flat 10k vs hilly 10k (50m/km gain)
            var flatCourse = List.of(flat(0, 10000));
            var hillyCourse = List.of(uphill(0, 10000, 0.08)); // 8% gradient

            var flatTarget = service.computeEffectiveTarget(flatCourse, profile(300, null, 20.0, 0.5));
            var hillyTarget = service.computeEffectiveTarget(hillyCourse, profile(300, null, 20.0, 0.5));

            assertTrue(hillyTarget.pace() > flatTarget.pace(),
                    "Hilly pace (%d) should be slower than flat pace (%d)"
                            .formatted(hillyTarget.pace(), flatTarget.pace()));
        }
    }

    @Nested
    class GenerateSegments {

        @Test
        void flatCourse_uniformPaces() {
            var course = List.of(flat(0, 5000), flat(5000, 10000));
            AthleteProfile p = profile(null, 300, 20.0, 0.5);

            List<PacingSegment> segments = service.generateSegments(course, p, 0.0);

            assertEquals(2, segments.size());
            // Both segments on flat terrain should have similar paces (within fatigue difference)
            double pace1 = segments.get(0).estimatedSegmentTime() / (5000.0 / 1000.0);
            double pace2 = segments.get(1).estimatedSegmentTime() / (5000.0 / 1000.0);
            assertTrue(Math.abs(pace1 - pace2) < 20,
                    "Flat segments should have similar paces: %.1f vs %.1f".formatted(pace1, pace2));
        }

        @Test
        void uphillSegment_slowerThanFlat() {
            var course = List.of(flat(0, 5000), uphill(5000, 10000, 0.05));
            AthleteProfile p = profile(null, 300, 20.0, 0.5);

            List<PacingSegment> segments = service.generateSegments(course, p, 0.0);

            double flatTime = segments.get(0).estimatedSegmentTime();
            double uphillTime = segments.get(1).estimatedSegmentTime();
            assertTrue(uphillTime > flatTime,
                    "Uphill segment (%.1fs) should take longer than flat (%.1fs)"
                            .formatted(uphillTime, flatTime));
        }

        @Test
        void downhillSegment_fasterThanFlat() {
            var course = List.of(downhill(0, 5000, -0.03), flat(5000, 10000));
            AthleteProfile p = profile(null, 300, 20.0, 0.5);

            List<PacingSegment> segments = service.generateSegments(course, p, 0.0);

            double downhillTime = segments.get(0).estimatedSegmentTime();
            double flatTime = segments.get(1).estimatedSegmentTime();
            assertTrue(downhillTime < flatTime,
                    "Downhill segment (%.1fs) should be faster than flat (%.1fs)"
                            .formatted(downhillTime, flatTime));
        }

        @Test
        void fatigueAccumulates() {
            var course = List.of(flat(0, 5000), flat(5000, 10000), flat(10000, 15000));
            AthleteProfile p = profile(null, 300, 20.0, 0.5);

            List<PacingSegment> segments = service.generateSegments(course, p, 0.0);

            assertTrue(segments.get(2).cumulativeFatigue() > segments.get(0).cumulativeFatigue(),
                    "Fatigue should accumulate over distance");
        }

        @Test
        void nutritionSuggestions_appear() {
            // Long enough course to trigger nutrition
            var course = List.of(flat(0, 5000), flat(5000, 10000), flat(10000, 15000),
                    flat(15000, 20000), flat(20000, 25000));
            AthleteProfile p = profile(null, 300, 20.0, 0.5);

            List<PacingSegment> segments = service.generateSegments(course, p, 0.0);

            boolean hasNutrition = segments.stream().anyMatch(s -> s.nutritionSuggestion() != null);
            assertTrue(hasNutrition, "Should have at least one nutrition suggestion on a 25km run");
        }
    }
}
