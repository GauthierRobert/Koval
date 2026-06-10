package com.koval.trainingplannerbackend.integration.nolio.write;

import com.koval.trainingplannerbackend.training.model.BlockType;
import com.koval.trainingplannerbackend.training.model.CyclingTraining;
import com.koval.trainingplannerbackend.training.model.RunningTraining;
import com.koval.trainingplannerbackend.training.model.Training;
import com.koval.trainingplannerbackend.training.model.WorkoutElement;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NolioWorkoutMapperTest {

    private final NolioWorkoutMapper mapper = new NolioWorkoutMapper();

    // ── Helpers ──────────────────────────────────────────────────────────────

    private static WorkoutElement block(BlockType type, Integer durationSeconds, Integer intensity, String label) {
        return new WorkoutElement(null, null, null, null,
                type, durationSeconds, null, label, null,
                intensity, null, null, null, null, null,
                null, null, null, null);
    }

    private static WorkoutElement ramp(int durationSeconds, int start, int end) {
        return new WorkoutElement(null, null, null, null, BlockType.RAMP, durationSeconds, null,
                null, null, null, start, end, null, null, null, null, null, null, null);
    }

    private static WorkoutElement set(int reps, List<WorkoutElement> children, Integer restDur, Integer restIntensity) {
        return new WorkoutElement(reps, children, restDur, restIntensity,
                null, null, null, null, null, null, null, null, null, null, null,
                null, null, null, null);
    }

    private static CyclingTraining cyclingTraining(List<WorkoutElement> blocks) {
        CyclingTraining training = new CyclingTraining();
        training.setTitle("Sweet Spot");
        training.setCreatedAt(LocalDateTime.of(2026, 6, 10, 8, 30));
        training.setEstimatedDurationSeconds(3600);
        training.setEstimatedDistance(40_000);
        training.setBlocks(blocks);
        return training;
    }

    // ── Top-level payload ────────────────────────────────────────────────────

    @Test
    void toPayload_carriesIdPartnerSportNameAndDate() {
        Map<String, Object> payload = mapper.toPayload(cyclingTraining(null), 42L, 250);

        assertEquals(42L, payload.get("id_partner"));
        assertEquals(14, payload.get("sport_id"));
        assertEquals("Sweet Spot", payload.get("name"));
        assertEquals("2026-06-10", payload.get("date_start"));
        assertEquals(3600, payload.get("duration"));
        assertEquals(40, payload.get("distance"));
        assertFalse(payload.containsKey("structured_workout"));
    }

    @Test
    void toPayload_runningSportId_andOmitsZeroDistance() {
        RunningTraining training = new RunningTraining();
        training.setTitle("Easy run");
        training.setEstimatedDistance(300);

        Map<String, Object> payload = mapper.toPayload(training, 7L, null);

        assertEquals(2, payload.get("sport_id"));
        assertFalse(payload.containsKey("distance"));
        assertTrue(payload.containsKey("date_start"));
    }

    // ── Structured workout ───────────────────────────────────────────────────

    @Test
    @SuppressWarnings("unchecked")
    void toPayload_cyclingWithFtp_emitsWattTargets() {
        Training training = cyclingTraining(List.of(block(BlockType.STEADY, 1200, 90, "Tempo")));

        Map<String, Object> payload = mapper.toPayload(training, 1L, 200);
        List<Map<String, Object>> steps = (List<Map<String, Object>>) payload.get("structured_workout");

        Map<String, Object> step = steps.get(0);
        assertEquals("step", step.get("type"));
        assertEquals("active", step.get("intensity_type"));
        assertEquals("duration", step.get("step_duration_type"));
        assertEquals(1200, step.get("step_duration_value"));
        assertEquals("power", step.get("target_type"));
        assertEquals(180, step.get("target_value_min"));
        assertEquals(180, step.get("target_value_max"));
        assertEquals("Tempo", step.get("comment"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void toPayload_cyclingWithoutFtp_fallsBackToPercentBounds() {
        Training training = cyclingTraining(List.of(block(BlockType.WARMUP, 600, 60, null)));

        Map<String, Object> payload = mapper.toPayload(training, 1L, null);
        Map<String, Object> step = ((List<Map<String, Object>>) payload.get("structured_workout")).get(0);

        assertEquals("warmup", step.get("intensity_type"));
        assertEquals(60, step.get("step_percent_low"));
        assertEquals(60, step.get("step_percent_high"));
        assertNull(step.get("target_value_max"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void toPayload_setWithActiveRest_mapsToRepetitionWithRestStep() {
        WorkoutElement interval = block(BlockType.INTERVAL, 300, 105, null);
        Training training = cyclingTraining(List.of(set(4, List.of(interval), 180, 60)));

        Map<String, Object> payload = mapper.toPayload(training, 1L, null);
        Map<String, Object> repetition = ((List<Map<String, Object>>) payload.get("structured_workout")).get(0);

        assertEquals("repetition", repetition.get("type"));
        assertEquals(4, repetition.get("value"));
        List<Map<String, Object>> steps = (List<Map<String, Object>>) repetition.get("steps");
        assertEquals(2, steps.size());
        Map<String, Object> rest = steps.get(1);
        assertEquals("rest", rest.get("intensity_type"));
        assertEquals(180, rest.get("step_duration_value"));
        assertEquals(60, rest.get("step_percent_low"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void toPayload_rampDown_usesRampDownIntensityType() {
        Training training = cyclingTraining(List.of(ramp(600, 90, 50)));

        Map<String, Object> payload = mapper.toPayload(training, 1L, 200);
        Map<String, Object> step = ((List<Map<String, Object>>) payload.get("structured_workout")).get(0);

        assertEquals("ramp_down", step.get("intensity_type"));
        assertEquals(100, step.get("target_value_min")); // 50% of 200W
        assertEquals(180, step.get("target_value_max")); // 90% of 200W
    }

    @Test
    @SuppressWarnings("unchecked")
    void toPayload_freeBlockWithoutIntensity_hasNoTargetAndOpenDuration() {
        Training training = cyclingTraining(List.of(block(BlockType.FREE, 900, null, null)));

        Map<String, Object> payload = mapper.toPayload(training, 1L, 200);
        Map<String, Object> step = ((List<Map<String, Object>>) payload.get("structured_workout")).get(0);

        assertEquals("no_target", step.get("target_type"));
        assertEquals(Boolean.TRUE, step.get("open_duration"));
    }
}
