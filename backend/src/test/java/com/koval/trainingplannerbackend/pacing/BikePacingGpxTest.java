package com.koval.trainingplannerbackend.pacing;

import com.koval.trainingplannerbackend.pacing.dto.AthleteProfile;
import com.koval.trainingplannerbackend.pacing.dto.PacingSegment;
import com.koval.trainingplannerbackend.pacing.dto.PacingSummary;
import com.koval.trainingplannerbackend.pacing.gpx.CourseSegment;
import com.koval.trainingplannerbackend.pacing.gpx.GpxParser;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * End-to-end pacing tests using real GPX files.
 * Verifies that TT bike produces a faster overall time than ROAD on both courses.
 */
class BikePacingGpxTest {

    private static final int FTP = 300;
    private static final int WEIGHT_KG = 70;
    private static final double SEGMENT_LENGTH_M = 200.0;

    private static GpxParser gpxParser;
    private static BikeSpeedService speedService;
    private static BikePacingService pacingService;

    private static List<CourseSegment> konaSegments;
    private static List<CourseSegment> niceSegments;

    @BeforeAll
    static void setUp() throws Exception {
        gpxParser = new GpxParser();
        speedService = new BikeSpeedService();
        pacingService = new BikePacingService(speedService);

        konaSegments = parseAndResample("gpx/MWC Kona Map Bike 24.gpx");
        niceSegments = parseAndResample("gpx/IM 70.3 Nice_Bike.gpx");

        assertFalse(konaSegments.isEmpty(), "Kona GPX should produce segments");
        assertFalse(niceSegments.isEmpty(), "Nice GPX should produce segments");
    }

    private static List<CourseSegment> parseAndResample(String resourcePath) throws Exception {
        try (InputStream is = BikePacingGpxTest.class.getClassLoader().getResourceAsStream(resourcePath)) {
            assertNotNull(is, "GPX resource not found: " + resourcePath);
            List<CourseSegment> raw = gpxParser.parse(is);
            return gpxParser.resampleToFixedDistance(raw, SEGMENT_LENGTH_M);
        }
    }

    private record PacingResult(List<PacingSegment> segments, PacingSummary summary, double totalTimeSec) {}

    private PacingResult runPacing(List<CourseSegment> course, String bikeType) {
        AthleteProfile baseProfile = new AthleteProfile(
                FTP, WEIGHT_KG, null, null,
                0.5, "MIXED", 20.0, 0.0,
                null, null, null, null, bikeType
        );

        BikePacingService.BikeTarget target = pacingService.computeEffectiveTarget(course, baseProfile, false);

        AthleteProfile profile = new AthleteProfile(
                FTP, WEIGHT_KG, null, null,
                0.5, "MIXED", 20.0, 0.0,
                target.power(), null, null, null, bikeType
        );

        List<PacingSegment> segments = pacingService.generateSegments(course, profile);
        PacingSummary summary = pacingService.buildSummary(segments, target.basis(), target.computed());
        double totalTime = segments.stream().mapToDouble(PacingSegment::estimatedSegmentTime).sum();

        return new PacingResult(segments, summary, totalTime);
    }

    @Test
    void kona_ttFasterThanRoad() {
        PacingResult tt = runPacing(konaSegments, "TT");
        PacingResult road = runPacing(konaSegments, "ROAD");

        assertTrue(tt.totalTimeSec < road.totalTimeSec,
                "Kona: TT (%.1fs) should be faster than ROAD (%.1fs)"
                        .formatted(tt.totalTimeSec, road.totalTimeSec));
    }

    @Test
    void kona_ttFasterThanRoadAero() {
        PacingResult tt = runPacing(konaSegments, "TT");
        PacingResult roadAero = runPacing(konaSegments, "ROAD_AERO");

        assertTrue(tt.totalTimeSec < roadAero.totalTimeSec,
                "Kona: TT (%.1fs) should be faster than ROAD_AERO (%.1fs)"
                        .formatted(tt.totalTimeSec, roadAero.totalTimeSec));
    }

    @Test
    void nice_ttFasterThanRoad() {
        PacingResult tt = runPacing(niceSegments, "TT");
        PacingResult road = runPacing(niceSegments, "ROAD");

        assertTrue(tt.totalTimeSec < road.totalTimeSec,
                "Nice: TT (%.1fs) should be faster than ROAD (%.1fs)"
                        .formatted(tt.totalTimeSec, road.totalTimeSec));
    }

    @Test
    void nice_ttFasterThanRoadAero() {
        PacingResult tt = runPacing(niceSegments, "TT");
        PacingResult roadAero = runPacing(niceSegments, "ROAD_AERO");

        assertTrue(tt.totalTimeSec < roadAero.totalTimeSec,
                "Nice: TT (%.1fs) should be faster than ROAD_AERO (%.1fs)"
                        .formatted(tt.totalTimeSec, roadAero.totalTimeSec));
    }

    @Test
    void kona_allSegmentsHavePositiveSpeedAndPower() {
        PacingResult tt = runPacing(konaSegments, "TT");

        for (PacingSegment seg : tt.segments) {
            assertTrue(seg.estimatedSpeedKmh() > 0, "Speed should be positive at distance %.0fm".formatted(seg.startDistance()));
            assertTrue(seg.targetPower() > 0, "Power should be positive at distance %.0fm".formatted(seg.startDistance()));
            assertTrue(seg.estimatedSegmentTime() > 0, "Segment time should be positive");
        }
    }

    @Test
    void nice_allSegmentsHavePositiveSpeedAndPower() {
        PacingResult road = runPacing(niceSegments, "ROAD_AERO");

        for (PacingSegment seg : road.segments) {
            assertTrue(seg.estimatedSpeedKmh() > 0, "Speed should be positive at distance %.0fm".formatted(seg.startDistance()));
            assertTrue(seg.targetPower() > 0, "Power should be positive at distance %.0fm".formatted(seg.startDistance()));
            assertTrue(seg.estimatedSegmentTime() > 0, "Segment time should be positive");
        }
    }
}
