package com.koval.trainingplannerbackend.integration.suunto;

import com.koval.trainingplannerbackend.training.model.BlockType;
import com.koval.trainingplannerbackend.training.model.SportType;
import com.koval.trainingplannerbackend.training.model.Training;
import com.koval.trainingplannerbackend.training.model.WorkoutElement;
import com.koval.trainingplannerbackend.training.model.WorkoutElementFlattener;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Builds a SuuntoPlus guide document from a {@link Training}. Guides are the vehicle for
 * planned/structured workouts on Suunto watches: a {@code sequence} of {@code fields} steps
 * (duration countdown + intensity target gauges) and {@code repeat} steps.
 *
 * <p>Unlike Garmin (which only accepts free-text intensity), guides display real target gauges,
 * so %-of-FTP targets are resolved to absolute watts against the athlete's FTP. A null FTP
 * degrades gracefully to duration-only steps — the push never fails on missing thresholds.
 *
 * <p>Hard limits from the guide spec: name ≤ 60 chars, shortDescription ≤ 23, step title ≤ 13,
 * externalId ≤ 64, repeat times 1–100 (no nesting), ≤ 1000 steps.
 */
public class SuuntoGuideBuilder {

    private static final int MAX_NAME = 60;
    private static final int MAX_DESCRIPTION = 256;
    private static final int MAX_SHORT_DESCRIPTION = 23;
    private static final int MAX_STEP_TITLE = 13;
    private static final int MAX_EXTERNAL_ID = 64;
    private static final int MAX_REPEAT_TIMES = 100;
    private static final int MAX_STEPS = 1000;

    /** Width of the target gauge around the computed watts (±5%). */
    private static final double TARGET_BAND = 0.05;

    // Suunto activity ids — keep in sync with SuuntoActivityMapper.
    private static final int ACTIVITY_RUNNING = 2;
    private static final int ACTIVITY_CYCLING = 3;
    private static final int ACTIVITY_SWIMMING = 21;

    public Map<String, Object> build(Training training, Integer ftp, String owner,
                                     String externalId, LocalDate scheduledDate) {
        Map<String, Object> guide = new LinkedHashMap<>();
        guide.put("type", "sequence");
        String title = training.getTitle() != null && !training.getTitle().isBlank()
                ? training.getTitle() : "Koval Workout";
        guide.put("name", truncate(title, MAX_NAME));
        String description = training.getDescription() != null && !training.getDescription().isBlank()
                ? training.getDescription() : "Planned workout from Koval";
        guide.put("description", truncate(description, MAX_DESCRIPTION));
        guide.put("shortDescription", truncate(title, MAX_SHORT_DESCRIPTION));
        if (owner != null && !owner.isBlank()) {
            guide.put("owner", owner);
        }
        guide.put("activities", List.of(toActivityId(training.getSportType())));
        guide.put("usage", "workout");
        guide.put("externalId", truncate(externalId, MAX_EXTERNAL_ID));
        if (scheduledDate != null) {
            guide.put("localDate", scheduledDate.toString()); // yyyy-MM-dd
        }

        boolean withPower = training.getSportType() == SportType.CYCLING && ftp != null && ftp > 0;
        List<Map<String, Object>> steps = buildSteps(training.getBlocks(), withPower ? ftp : null);
        if (steps.size() > MAX_STEPS) {
            steps = steps.subList(0, MAX_STEPS);
        }
        guide.put("steps", steps);
        return guide;
    }

    private List<Map<String, Object>> buildSteps(List<WorkoutElement> elements, Integer ftp) {
        List<Map<String, Object>> steps = new ArrayList<>();
        if (elements == null) return steps;
        for (WorkoutElement el : elements) {
            if (el == null) continue;
            if (el.isSet()) {
                appendSet(el, ftp, steps);
            } else {
                appendLeaf(el, ftp, steps);
            }
        }
        return steps;
    }

    /**
     * Single-depth sets within the repeat limit map to a native {@code repeat} step (the rest
     * between reps becomes a trailing step inside the loop). Nested or oversized sets are
     * flattened to plain leaf steps — guides don't allow nested repeats.
     */
    private void appendSet(WorkoutElement set, Integer ftp, List<Map<String, Object>> out) {
        int reps = set.repetitions() != null ? set.repetitions() : 1;
        boolean hasNestedSet = set.elements().stream().anyMatch(WorkoutElement::isSet);

        if (hasNestedSet || reps > MAX_REPEAT_TIMES) {
            for (WorkoutElement leaf : WorkoutElementFlattener.flatten(List.of(set))) {
                appendLeaf(leaf, ftp, out);
            }
            return;
        }

        List<Map<String, Object>> children = new ArrayList<>();
        for (WorkoutElement child : set.elements()) {
            appendLeaf(child, ftp, children);
        }
        if (set.restDurationSeconds() != null && set.restDurationSeconds() > 0) {
            children.add(fieldsStep("Rest", set.restDurationSeconds(), null,
                    set.restIntensity() != null && set.restIntensity() > 0 ? set.restIntensity() : null, ftp));
        }
        if (children.isEmpty()) return;

        if (reps <= 1) {
            out.addAll(children);
            return;
        }
        Map<String, Object> repeat = new LinkedHashMap<>();
        repeat.put("type", "repeat");
        repeat.put("times", reps);
        repeat.put("steps", children);
        out.add(repeat);
    }

    private void appendLeaf(WorkoutElement el, Integer ftp, List<Map<String, Object>> out) {
        if (el.durationSeconds() == null && el.distanceMeters() == null) return;
        out.add(fieldsStep(leafTitle(el), el.durationSeconds(), el.distanceMeters(),
                leafIntensity(el), ftp));
    }

    private Map<String, Object> fieldsStep(String title, Integer durationSeconds,
                                           Integer distanceMeters, Integer intensityPercent, Integer ftp) {
        Map<String, Object> step = new LinkedHashMap<>();
        step.put("type", "fields");
        step.put("title", truncate(title, MAX_STEP_TITLE));

        List<Map<String, Object>> fields = new ArrayList<>();
        if (durationSeconds != null) {
            fields.add(Map.of("type", "StepDurationCountdownField"));
        } else {
            fields.add(Map.of("type", "StepDistanceCountdownField"));
        }
        if (intensityPercent != null && intensityPercent > 0 && ftp != null) {
            int watts = (int) Math.round(intensityPercent / 100.0 * ftp);
            Map<String, Object> target = new LinkedHashMap<>();
            target.put("type", "TargetPowerField");
            target.put("min", (int) Math.round(watts * (1 - TARGET_BAND)));
            target.put("max", (int) Math.round(watts * (1 + TARGET_BAND)));
            fields.add(target);
        }
        step.put("fields", fields);

        Map<String, Object> condition = new LinkedHashMap<>();
        if (durationSeconds != null) {
            condition.put("type", "stepDuration");
            condition.put("value", durationSeconds.doubleValue());
        } else if (distanceMeters != null) {
            condition.put("type", "stepDistance");
            condition.put("value", distanceMeters.doubleValue());
        } else {
            condition.put("type", "manualLap");
        }
        // No target stepId — the watch advances to the next step in the sequence.
        step.put("transitions", List.of(Map.of("condition", condition)));
        return step;
    }

    /** Target % for a leaf — RAMP blocks collapse to the midpoint (guides have no native ramp). */
    private static Integer leafIntensity(WorkoutElement el) {
        if (el.intensityTarget() != null) return el.intensityTarget();
        if (el.intensityStart() != null && el.intensityEnd() != null) {
            return (el.intensityStart() + el.intensityEnd()) / 2;
        }
        return null;
    }

    private static String leafTitle(WorkoutElement el) {
        if (el.label() != null && !el.label().isBlank()) return el.label();
        BlockType type = el.type();
        if (type == null) return "Step";
        return switch (type) {
            case WARMUP -> "Warmup";
            case COOLDOWN -> "Cooldown";
            case PAUSE -> "Rest";
            case RAMP -> "Ramp";
            case STEADY -> "Steady";
            case FREE -> "Free";
            default -> "Interval";
        };
    }

    private static int toActivityId(SportType sport) {
        if (sport == null) return ACTIVITY_CYCLING;
        return switch (sport) {
            case RUNNING -> ACTIVITY_RUNNING;
            case SWIMMING -> ACTIVITY_SWIMMING;
            default -> ACTIVITY_CYCLING;
        };
    }

    private static String truncate(String value, int max) {
        if (value == null) return "";
        return value.length() <= max ? value : value.substring(0, max);
    }
}
