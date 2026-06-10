package com.koval.trainingplannerbackend.integration.nolio.webhook;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.koval.trainingplannerbackend.training.model.BlockType;
import com.koval.trainingplannerbackend.training.model.SportType;
import com.koval.trainingplannerbackend.training.model.Training;
import com.koval.trainingplannerbackend.training.model.WorkoutElement;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NolioPlannedTrainingImportMapperTest {

    private final NolioPlannedTrainingImportMapper mapper = new NolioPlannedTrainingImportMapper();
    private final ObjectMapper json = new ObjectMapper();

    private JsonNode parse(String content) {
        try {
            return json.readTree(content);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    @Test
    void map_basicCyclingWorkout_carriesMetadataAndUnits() {
        JsonNode planned = parse("""
                {
                  "nolio_id": 123,
                  "name": "Endurance ride",
                  "sport_id": 14,
                  "date_start": "2026-06-12",
                  "duration": 5400,
                  "distance": 45.0,
                  "load_coggan": 85.4,
                  "description": "Stay seated"
                }
                """);

        Training training = mapper.map(planned, 250);

        assertEquals(SportType.CYCLING, training.getSportType());
        assertEquals("Endurance ride", training.getTitle());
        assertEquals("Stay seated", training.getDescription());
        assertEquals(5400, training.getEstimatedDurationSeconds());
        assertEquals(45_000, training.getEstimatedDistance());
        assertEquals(85, training.getEstimatedTss());
    }

    @Test
    void map_unsupportedSport_returnsNull() {
        JsonNode planned = parse("{\"sport_id\": 30, \"name\": \"Yoga\"}");
        assertNull(mapper.map(planned, null));
    }

    @Test
    void map_powerTargetsWithFtp_convertToPercent() {
        JsonNode planned = parse("""
                {
                  "sport_id": 14,
                  "name": "Threshold",
                  "structured_workout": [
                    {"type": "step", "intensity_type": "active", "step_duration_type": "duration",
                     "step_duration_value": 1200, "target_type": "power",
                     "target_value_min": 200, "target_value_max": 220}
                  ]
                }
                """);

        Training training = mapper.map(planned, 200);
        WorkoutElement step = training.getBlocks().get(0);

        assertEquals(BlockType.STEADY, step.type());
        assertEquals(1200, step.durationSeconds());
        assertEquals(105, step.intensityTarget()); // avg(100%, 110%)
    }

    @Test
    void map_repetitionWithNestedSteps_buildsSet() {
        JsonNode planned = parse("""
                {
                  "sport_id": 2,
                  "name": "Intervals",
                  "structured_workout": [
                    {"type": "repetition", "value": 3, "steps": [
                      {"type": "step", "intensity_type": "active", "step_duration_type": "duration",
                       "step_duration_value": 180, "target_type": "pace",
                       "target_value_min": 4.05, "target_value_max": 4.23},
                      {"type": "step", "intensity_type": "rest", "step_duration_type": "duration",
                       "step_duration_value": 120, "target_type": "no_target"}
                    ]}
                  ]
                }
                """);

        Training training = mapper.map(planned, null);
        WorkoutElement set = training.getBlocks().get(0);

        assertTrue(set.isSet());
        assertEquals(3, set.repetitions());
        assertEquals(2, set.elements().size());
        WorkoutElement active = set.elements().get(0);
        assertEquals(BlockType.STEADY, active.type());
        assertNull(active.intensityTarget());
        assertTrue(active.description().startsWith("Pace "));
        assertTrue(active.description().contains("/km"));
        assertEquals(BlockType.PAUSE, set.elements().get(1).type());
    }

    @Test
    void map_rampDown_reversesStartAndEnd() {
        JsonNode planned = parse("""
                {
                  "sport_id": 14,
                  "name": "Ramp",
                  "structured_workout": [
                    {"type": "step", "intensity_type": "ramp_down", "step_duration_type": "duration",
                     "step_duration_value": 600, "target_type": "power",
                     "target_value_min": 100, "target_value_max": 180}
                  ]
                }
                """);

        Training training = mapper.map(planned, 200);
        WorkoutElement ramp = training.getBlocks().get(0);

        assertEquals(BlockType.RAMP, ramp.type());
        assertEquals(90, ramp.intensityStart()); // 180W of 200 FTP
        assertEquals(50, ramp.intensityEnd());   // 100W of 200 FTP
    }

    @Test
    void map_distanceStep_mapsToDistanceMeters() {
        JsonNode planned = parse("""
                {
                  "sport_id": 19,
                  "name": "Swim",
                  "structured_workout": [
                    {"type": "step", "intensity_type": "warmup", "step_duration_type": "distance",
                     "step_duration_value": 400, "target_type": "no_target"}
                  ]
                }
                """);

        Training training = mapper.map(planned, null);
        WorkoutElement step = training.getBlocks().get(0);

        assertEquals(SportType.SWIMMING, training.getSportType());
        assertEquals(BlockType.WARMUP, step.type());
        assertEquals(400, step.distanceMeters());
        assertNull(step.durationSeconds());
    }
}
