package com.koval.trainingplannerbackend.integration.suunto;

import com.koval.trainingplannerbackend.training.model.BlockType;
import com.koval.trainingplannerbackend.training.model.CyclingTraining;
import com.koval.trainingplannerbackend.training.model.RunningTraining;
import com.koval.trainingplannerbackend.training.model.SportType;
import com.koval.trainingplannerbackend.training.model.Training;
import com.koval.trainingplannerbackend.training.model.WorkoutElement;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SuuntoGuideBuilderTest {

    private final SuuntoGuideBuilder builder = new SuuntoGuideBuilder();

    private static WorkoutElement leaf(BlockType type, Integer durationSeconds, Integer intensity) {
        return new WorkoutElement(null, null, null, null, type, durationSeconds, null,
                null, null, intensity, null, null, null, null, null, null, null, null, null);
    }

    private static WorkoutElement ramp(int durationSeconds, int start, int end) {
        return new WorkoutElement(null, null, null, null, BlockType.RAMP, durationSeconds, null,
                null, null, null, start, end, null, null, null, null, null, null, null);
    }

    private static WorkoutElement set(Integer reps, Integer restSeconds, Integer restIntensity,
                                      WorkoutElement... children) {
        return new WorkoutElement(reps, List.of(children), restSeconds, restIntensity,
                null, null, null, null, null, null, null, null, null, null, null, null, null, null, null);
    }

    private static Training cycling(String title, WorkoutElement... blocks) {
        CyclingTraining training = new CyclingTraining();
        training.setTitle(title);
        training.setSportType(SportType.CYCLING);
        training.setBlocks(List.of(blocks));
        return training;
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> steps(Map<String, Object> guide) {
        return (List<Map<String, Object>>) guide.get("steps");
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> fields(Map<String, Object> step) {
        return (List<Map<String, Object>>) step.get("fields");
    }

    private static Map<String, Object> powerField(Map<String, Object> step) {
        return fields(step).stream()
                .filter(f -> "TargetPowerField".equals(f.get("type")))
                .findFirst().orElse(null);
    }

    @Test
    void build_metadata_withinSpecLimits() {
        String longTitle = "A".repeat(80);
        Map<String, Object> guide = builder.build(cycling(longTitle, leaf(BlockType.STEADY, 600, 70)),
                250, "Koval", "koval-" + "x".repeat(80), LocalDate.of(2026, 6, 10));

        assertEquals("sequence", guide.get("type"));
        assertEquals(60, ((String) guide.get("name")).length());
        assertEquals(23, ((String) guide.get("shortDescription")).length());
        assertEquals(64, ((String) guide.get("externalId")).length());
        assertEquals("Koval", guide.get("owner"));
        assertEquals("workout", guide.get("usage"));
        assertEquals(List.of(3), guide.get("activities"));
        assertEquals("2026-06-10", guide.get("localDate"));
    }

    @Test
    void build_percentOfFtp_resolvedToWatts() {
        // 90% of 250W FTP = 225W; ±5% band → 214–236
        Map<String, Object> guide = builder.build(
                cycling("W", leaf(BlockType.INTERVAL, 300, 90)), 250, "Koval", "id", null);

        Map<String, Object> target = powerField(steps(guide).get(0));
        assertEquals(214, target.get("min"));
        assertEquals(236, target.get("max"));
    }

    @Test
    void build_ramp_collapsesToMidpoint() {
        // 50→100% of 200W FTP → midpoint 75% = 150W
        Map<String, Object> guide = builder.build(
                cycling("W", ramp(600, 50, 100)), 200, "Koval", "id", null);

        Map<String, Object> step = steps(guide).get(0);
        assertEquals("Ramp", step.get("title"));
        Map<String, Object> target = powerField(step);
        assertEquals((int) Math.round(150 * 0.95), target.get("min"));
        assertEquals((int) Math.round(150 * 1.05), target.get("max"));
    }

    @Test
    void build_singleDepthSet_becomesRepeatStep() {
        Map<String, Object> guide = builder.build(
                cycling("W", set(4, 120, 60, leaf(BlockType.INTERVAL, 300, 105))),
                250, "Koval", "id", null);

        Map<String, Object> repeat = steps(guide).get(0);
        assertEquals("repeat", repeat.get("type"));
        assertEquals(4, repeat.get("times"));
        List<Map<String, Object>> children = steps(repeat);
        assertEquals(2, children.size()); // interval + active rest
        assertEquals("Rest", children.get(1).get("title"));
    }

    @Test
    void build_nestedSet_isFlattened() {
        WorkoutElement inner = set(2, null, null, leaf(BlockType.INTERVAL, 60, 110));
        WorkoutElement outer = set(3, null, null, inner);
        Map<String, Object> guide = builder.build(cycling("W", outer), 250, "Koval", "id", null);

        // 3 × 2 = 6 flat fields steps, no repeat (guides forbid nested repeats)
        List<Map<String, Object>> steps = steps(guide);
        assertEquals(6, steps.size());
        assertTrue(steps.stream().allMatch(s -> "fields".equals(s.get("type"))));
    }

    @Test
    void build_oversizedReps_isFlattened() {
        Map<String, Object> guide = builder.build(
                cycling("W", set(150, null, null, leaf(BlockType.INTERVAL, 30, 120))),
                250, "Koval", "id", null);

        assertEquals(150, steps(guide).size());
        assertTrue(steps(guide).stream().noneMatch(s -> "repeat".equals(s.get("type"))));
    }

    @Test
    void build_nullFtp_omitsPowerTarget() {
        Map<String, Object> guide = builder.build(
                cycling("W", leaf(BlockType.INTERVAL, 300, 90)), null, "Koval", "id", null);

        assertNull(powerField(steps(guide).get(0)));
        // Duration countdown still present
        assertEquals("StepDurationCountdownField", fields(steps(guide).get(0)).get(0).get("type"));
    }

    @Test
    void build_running_usesDurationOnlySteps() {
        RunningTraining training = new RunningTraining();
        training.setTitle("Run");
        training.setSportType(SportType.RUNNING);
        training.setBlocks(List.of(leaf(BlockType.INTERVAL, 300, 90)));

        Map<String, Object> guide = builder.build(training, 250, "Koval", "id", null);

        assertEquals(List.of(2), guide.get("activities"));
        assertNull(powerField(steps(guide).get(0))); // power targets are cycling-only
    }

    @Test
    void build_stepTitle_truncatedTo13Chars() {
        WorkoutElement labelled = new WorkoutElement(null, null, null, null,
                BlockType.STEADY, 600, null, "A very long block label", null,
                70, null, null, null, null, null, null, null, null, null);
        Map<String, Object> guide = builder.build(cycling("W", labelled), 250, "Koval", "id", null);

        assertEquals(13, ((String) steps(guide).get(0).get("title")).length());
    }

    @Test
    void build_durationTransition_attached() {
        Map<String, Object> guide = builder.build(
                cycling("W", leaf(BlockType.WARMUP, 900, 55)), 250, "Koval", "id", null);

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> transitions =
                (List<Map<String, Object>>) steps(guide).get(0).get("transitions");
        @SuppressWarnings("unchecked")
        Map<String, Object> condition = (Map<String, Object>) transitions.get(0).get("condition");
        assertEquals("stepDuration", condition.get("type"));
        assertEquals(900.0, condition.get("value"));
    }
}
