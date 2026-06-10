package com.koval.trainingplannerbackend.integration.nolio.webhook;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.koval.trainingplannerbackend.training.history.CompletedSession;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class NolioSessionImportMapperTest {

    private final NolioSessionImportMapper mapper = new NolioSessionImportMapper();
    private final ObjectMapper json = new ObjectMapper();

    private JsonNode parse(String content) {
        try {
            return json.readTree(content);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    @Test
    void map_fullWorkout_convertsUnitsAndDate() {
        JsonNode workout = parse("""
                {
                  "nolio_id": 4986055,
                  "name": "Bike",
                  "sport_id": 14,
                  "date_start": "2026-06-03",
                  "hour_start": "08:15:00",
                  "duration": 12965,
                  "distance": 77.71,
                  "rpe": 3,
                  "avg_watt": 184.0,
                  "np": 184.4,
                  "load_coggan": 131.65
                }
                """);

        CompletedSession session = mapper.map(workout);

        assertEquals("4986055", session.getNolioActivityId());
        assertEquals("Bike", session.getTitle());
        assertEquals("CYCLING", session.getSportType());
        assertEquals(LocalDateTime.of(2026, 6, 3, 8, 15), session.getCompletedAt());
        assertEquals(12965, session.getTotalDurationSeconds());
        assertEquals(77710.0, session.getTotalDistance());
        assertEquals(184.0, session.getAvgPower());
        assertEquals(184.4, session.getNormalizedPower());
        assertEquals(3, session.getRpe());
        assertEquals(131.65, session.getTss());
    }

    @Test
    void map_missingHourStart_fallsBackToStartOfDay() {
        JsonNode workout = parse("""
                {"nolio_id": 1, "name": "Run", "sport_id": 2, "date_start": "2026-06-01",
                 "hour_start": "", "duration": 3600}
                """);

        CompletedSession session = mapper.map(workout);

        assertEquals(LocalDateTime.of(2026, 6, 1, 0, 0), session.getCompletedAt());
        assertEquals("RUNNING", session.getSportType());
        assertNull(session.getTotalDistance());
    }

    @Test
    void applyUpdate_overwritesScalarsButKeepsLinks() {
        CompletedSession existing = new CompletedSession();
        existing.setStravaActivityId("strava-1");
        existing.setNolioActivityId("9");
        existing.setTitle("Old");
        existing.setTotalDurationSeconds(100);

        CompletedSession incoming = mapper.map(parse("""
                {"nolio_id": 9, "name": "New title", "sport_id": 14,
                 "date_start": "2026-06-02", "duration": 200, "rpe": 7}
                """));
        mapper.applyUpdate(existing, incoming);

        assertEquals("New title", existing.getTitle());
        assertEquals(200, existing.getTotalDurationSeconds());
        assertEquals(7, existing.getRpe());
        assertEquals("strava-1", existing.getStravaActivityId());
        assertEquals("9", existing.getNolioActivityId());
    }
}
