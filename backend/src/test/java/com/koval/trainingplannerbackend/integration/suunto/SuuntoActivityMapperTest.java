package com.koval.trainingplannerbackend.integration.suunto;

import com.koval.trainingplannerbackend.training.history.CompletedSession;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class SuuntoActivityMapperTest {

    private final SuuntoActivityMapper mapper = new SuuntoActivityMapper();

    @Test
    void map_fullSummary_mapsAllFields() {
        Map<String, Object> workout = Map.of(
                "workoutKey", "abc123",
                "workoutName", "Morning Ride",
                "activityId", 3,
                "startTime", 1750000000000L,
                "totalTime", 5400.5,
                "totalDistance", 45000.0,
                "avgHR", 142,
                "avgSpeed", 8.33);

        CompletedSession session = mapper.map(workout);

        assertEquals("abc123", session.getSuuntoActivityId());
        assertEquals("Morning Ride", session.getTitle());
        assertEquals("CYCLING", session.getSportType());
        assertNotNull(session.getCompletedAt());
        assertEquals(5401, session.getTotalDurationSeconds()); // rounded
        assertEquals(45000.0, session.getTotalDistance());
        assertEquals(142.0, session.getAvgHR());
        assertEquals(8.33, session.getAvgSpeed());
    }

    @Test
    void map_runningActivityId_mapsRunning() {
        assertEquals("RUNNING", mapper.map(Map.of("activityId", 2)).getSportType());
        assertEquals("RUNNING", mapper.map(Map.of("activityId", 22)).getSportType()); // trail running
    }

    @Test
    void map_swimmingActivityId_mapsSwimming() {
        assertEquals("SWIMMING", mapper.map(Map.of("activityId", 21)).getSportType());
    }

    @Test
    void map_unknownActivityId_fallsBackToTextualHint() {
        assertEquals("CYCLING", mapper.map(Map.of("activityId", 99, "workoutName", "Indoor bike session"))
                .getSportType());
        assertEquals("OTHER", mapper.map(Map.of("activityId", 99)).getSportType());
    }

    @Test
    void map_missingTitle_usesDefault() {
        Map<String, Object> workout = new HashMap<>();
        workout.put("workoutKey", "k");
        assertEquals("Suunto Workout", mapper.map(workout).getTitle());
    }
}
