package com.koval.trainingplannerbackend.integration.garmin;

import com.koval.trainingplannerbackend.training.model.BlockType;
import com.koval.trainingplannerbackend.training.model.SportType;
import com.koval.trainingplannerbackend.training.model.Training;
import com.koval.trainingplannerbackend.training.model.WorkoutElement;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Builds a Garmin Training API workout payload from a {@link Training}.
 *
 * <p>Garmin's workout schema groups segments under {@code steps}; each step has a
 * {@code stepOrder}, {@code type} ({@code WarmUp}/{@code Interval}/{@code Recovery}/
 * {@code Rest}/{@code CoolDown}), a duration condition, and an optional target. Like Polar,
 * Garmin doesn't accept arbitrary "% of FTP" targets, so we encode the intensity in the step
 * description and emit duration-based steps. RAMP blocks are flattened to a midpoint step.
 */
public class GarminWorkoutMapper {

    public Map<String, Object> map(Training training) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("workoutName", training.getTitle() != null ? training.getTitle() : "Koval Workout");
        payload.put("sport", toGarminSport(training.getSportType()));
        if (training.getDescription() != null && !training.getDescription().isBlank()) {
            payload.put("description", training.getDescription());
        }
        if (training.getEstimatedDurationSeconds() != null) {
            payload.put("estimatedDurationInSecs", training.getEstimatedDurationSeconds());
        }
        if (training.getEstimatedDistance() != null) {
            payload.put("estimatedDistanceInMeters", training.getEstimatedDistance());
        }

        List<Map<String, Object>> steps = new ArrayList<>();
        if (training.getBlocks() != null) {
            int[] order = {1};
            for (WorkoutElement el : training.getBlocks()) {
                flatten(el, 1, steps, order);
            }
        }
        payload.put("steps", steps);
        return payload;
    }

    private void flatten(WorkoutElement el, int repsMultiplier,
                         List<Map<String, Object>> out, int[] order) {
        if (el == null) return;
        if (el.isSet()) {
            int reps = el.repetitions() != null ? el.repetitions() : 1;
            int total = reps * repsMultiplier;
            for (int i = 0; i < total; i++) {
                for (WorkoutElement child : el.elements()) {
                    flatten(child, 1, out, order);
                }
                if (el.restDurationSeconds() != null && el.restDurationSeconds() > 0 && i < total - 1) {
                    out.add(step(order, BlockType.PAUSE, el.restDurationSeconds(), null,
                            el.restIntensity() != null && el.restIntensity() > 0
                                    ? "Recovery " + el.restIntensity() + "%"
                                    : "Recovery"));
                }
            }
            return;
        }
        Integer dur = el.durationSeconds();
        Integer dist = el.distanceMeters();
        if (dur == null && dist == null) return;
        out.add(step(order, el.type(), dur, dist, leafLabel(el)));
    }

    private Map<String, Object> step(int[] order, BlockType type, Integer durationSec,
                                     Integer distanceMeters, String description) {
        Map<String, Object> step = new LinkedHashMap<>();
        step.put("stepOrder", order[0]++);
        step.put("type", mapType(type));
        step.put("description", description != null ? description : "Step");

        Map<String, Object> durationCondition = new LinkedHashMap<>();
        if (durationSec != null) {
            durationCondition.put("conditionType", "TIME");
            durationCondition.put("value", durationSec);
            durationCondition.put("unit", "seconds");
        } else if (distanceMeters != null) {
            durationCondition.put("conditionType", "DISTANCE");
            durationCondition.put("value", distanceMeters);
            durationCondition.put("unit", "meters");
        } else {
            durationCondition.put("conditionType", "LAP_BUTTON");
        }
        step.put("durationCondition", durationCondition);
        return step;
    }

    private static String leafLabel(WorkoutElement el) {
        String base = el.label();
        if (base == null || base.isBlank()) {
            base = el.type() != null ? capital(el.type().name()) : "Step";
        }
        Integer intensity = el.intensityTarget();
        if (intensity == null && el.intensityStart() != null && el.intensityEnd() != null) {
            intensity = (el.intensityStart() + el.intensityEnd()) / 2;
        }
        return intensity != null ? base + " " + intensity + "%" : base;
    }

    private static String mapType(BlockType type) {
        if (type == null) return "Interval";
        return switch (type) {
            case WARMUP -> "WarmUp";
            case COOLDOWN -> "CoolDown";
            case PAUSE -> "Rest";
            default -> "Interval";
        };
    }

    private static String toGarminSport(SportType sport) {
        if (sport == null) return "GENERIC";
        return switch (sport) {
            case CYCLING -> "CYCLING";
            case RUNNING -> "RUNNING";
            case SWIMMING -> "LAP_SWIMMING";
            default -> "GENERIC";
        };
    }

    private static String capital(String s) {
        return s.isEmpty() ? s : Character.toUpperCase(s.charAt(0)) + s.substring(1).toLowerCase();
    }
}
