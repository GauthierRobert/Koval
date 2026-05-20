package com.koval.trainingplannerbackend.integration.polar;

import com.koval.trainingplannerbackend.training.model.BlockType;
import com.koval.trainingplannerbackend.training.model.SportType;
import com.koval.trainingplannerbackend.training.model.Training;
import com.koval.trainingplannerbackend.training.model.WorkoutElement;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Builds a Polar AccessLink training-target payload from one of our {@link Training} documents.
 *
 * <p>Polar's free-tier training targets accept HR or speed zones but <em>not</em> power zones. Since
 * our intensity model is power-centric (% of FTP), we encode the % into the phase name (e.g.
 * "Interval 105% FTP") so the athlete sees the target on the watch, and emit duration-based phases.
 *
 * <p>RAMP blocks are flattened to a single step at the midpoint intensity — Polar has no native
 * ramp phase concept.
 */
public class PolarTrainingTargetMapper {

    public Map<String, Object> map(Training training, LocalDate scheduledDate) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("name", training.getTitle() != null ? training.getTitle() : "Koval Workout");
        payload.put("sport", toPolarSport(training.getSportType()));
        if (scheduledDate != null) {
            payload.put("date", scheduledDate.toString());
        }
        if (training.getDescription() != null && !training.getDescription().isBlank()) {
            payload.put("description", training.getDescription());
        }
        if (training.getEstimatedDurationSeconds() != null) {
            payload.put("duration", "PT" + training.getEstimatedDurationSeconds() + "S");
        }
        if (training.getEstimatedDistance() != null) {
            payload.put("distance", training.getEstimatedDistance().doubleValue());
        }

        List<Map<String, Object>> steps = new ArrayList<>();
        if (training.getBlocks() != null) {
            for (WorkoutElement el : training.getBlocks()) {
                flatten(el, 1, steps);
            }
        }
        if (!steps.isEmpty()) {
            // Polar's training target schema groups phases under "exercises" → "steps".
            Map<String, Object> exercise = new LinkedHashMap<>();
            exercise.put("name", training.getTitle() != null ? training.getTitle() : "Workout");
            exercise.put("steps", steps);
            payload.put("exercises", List.of(exercise));
        }
        return payload;
    }

    private void flatten(WorkoutElement el, int repsMultiplier, List<Map<String, Object>> out) {
        if (el == null) return;
        if (el.isSet()) {
            int reps = el.repetitions() != null ? el.repetitions() : 1;
            for (int i = 0; i < reps * repsMultiplier; i++) {
                for (WorkoutElement child : el.elements()) {
                    flatten(child, 1, out);
                }
                if (el.restDurationSeconds() != null && el.restDurationSeconds() > 0
                        && i < reps * repsMultiplier - 1) {
                    out.add(step(BlockType.PAUSE, el.restDurationSeconds(), null,
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
        out.add(step(el.type(), dur, dist, leafLabel(el)));
    }

    private Map<String, Object> step(BlockType type, Integer durationSec, Integer distanceMeters, String label) {
        Map<String, Object> step = new LinkedHashMap<>();
        step.put("name", label != null ? label : "Step");
        step.put("type", mapType(type));
        if (durationSec != null) step.put("duration", "PT" + durationSec + "S");
        if (distanceMeters != null) step.put("distance", distanceMeters.doubleValue());
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
        if (type == null) return "WORK";
        return switch (type) {
            case WARMUP -> "WARM_UP";
            case COOLDOWN -> "COOL_DOWN";
            case PAUSE -> "REST";
            default -> "WORK";
        };
    }

    private static String toPolarSport(SportType sport) {
        if (sport == null) return "OTHER";
        return switch (sport) {
            case CYCLING -> "CYCLING";
            case RUNNING -> "RUNNING";
            case SWIMMING -> "SWIMMING";
            default -> "OTHER";
        };
    }

    private static String capital(String s) {
        return s.isEmpty() ? s : s.charAt(0) + s.substring(1).toLowerCase();
    }
}
