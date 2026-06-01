package com.koval.trainingplannerbackend.integration.strava;

import com.koval.trainingplannerbackend.training.metrics.FitGpsExtractor;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies that {@link FitFileBuilder} now preserves GPS coordinates (position_lat /
 * position_long) when the Strava streams include a lat/lng track.
 *
 * <p>Uses a real "export original" FIT from Strava ({@code fit/morning_run_strava.fit}, the
 * 72&nbsp;KB device file that carries GPS) as the source of realistic coordinates. The flow
 * mirrors production: real track -> streams map -> {@code buildFromStreams} -> re-parse and
 * assert the GPS survives the round trip.
 */
class FitFileBuilderGpsTest {

    private static final String FIXTURE = "fit/morning_run_strava.fit";
    /** Semicircle quantization is ~8.4e-8 deg; 1e-5 leaves comfortable margin. */
    private static final double COORD_TOLERANCE_DEG = 1e-5;

    @Test
    void originalStravaFit_carriesGpsTrack() throws Exception {
        FitGpsExtractor.Track track = FitGpsExtractor.extract(readFixture());

        assertFalse(track.isEmpty(), "The original Strava export should contain a GPS track");
        for (int i = 0; i < track.size(); i++) {
            double lat = track.lat().get(i);
            double lng = track.lng().get(i);
            assertTrue(lat >= -90.0 && lat <= 90.0, "latitude out of range: " + lat);
            assertTrue(lng >= -180.0 && lng <= 180.0, "longitude out of range: " + lng);
        }
    }

    @Test
    void buildFromStreams_roundTripsGpsFromRealActivity() throws Exception {
        FitGpsExtractor.Track source = FitGpsExtractor.extract(readFixture());
        assertFalse(source.isEmpty(), "fixture must have GPS for this test to be meaningful");

        Map<String, List<? extends Number>> streams = streamsFromTrack(source);

        byte[] built = new FitFileBuilder().buildFromStreams(
                streams, "RUNNING", LocalDateTime.of(2026, 6, 1, 7, 0),
                source.size(), source.size(),
                0.0, 150.0, 85.0, 3.0);

        FitGpsExtractor.Track rebuilt = FitGpsExtractor.extract(built);

        assertEquals(source.size(), rebuilt.size(),
                "every GPS sample should be re-encoded into the rebuilt FIT");
        for (int i = 0; i < source.size(); i++) {
            assertEquals(source.lat().get(i), rebuilt.lat().get(i), COORD_TOLERANCE_DEG,
                    "latitude mismatch at record " + i);
            assertEquals(source.lng().get(i), rebuilt.lng().get(i), COORD_TOLERANCE_DEG,
                    "longitude mismatch at record " + i);
        }
    }

    @Test
    void buildFromStreams_withoutGps_producesNoGpsRecords() {
        Map<String, List<? extends Number>> streams = new HashMap<>();
        streams.put("time", List.of(0, 1, 2, 3, 4));
        streams.put("watts", List.of(200, 210, 205, 215, 220));

        byte[] built = new FitFileBuilder().buildFromStreams(
                streams, "CYCLING", LocalDateTime.of(2026, 6, 1, 7, 0),
                5, 5, 210.0, 150.0, 90.0, 8.0);

        assertTrue(FitGpsExtractor.extract(built).isEmpty(),
                "a FIT built from GPS-less streams must contain no position records");
    }

    /** Build a minimal streams map (time + lat + lng) aligned to a GPS track. */
    private static Map<String, List<? extends Number>> streamsFromTrack(FitGpsExtractor.Track track) {
        List<Integer> time = new ArrayList<>(track.size());
        for (int i = 0; i < track.size(); i++) {
            time.add(i);
        }
        Map<String, List<? extends Number>> streams = new HashMap<>();
        streams.put("time", time);
        streams.put("lat", track.lat());
        streams.put("lng", track.lng());
        return streams;
    }

    private static byte[] readFixture() throws Exception {
        try (InputStream is = FitFileBuilderGpsTest.class.getClassLoader().getResourceAsStream(FIXTURE)) {
            assertNotNull(is, "FIT fixture not found on classpath: " + FIXTURE);
            return is.readAllBytes();
        }
    }
}
