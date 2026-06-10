package com.koval.trainingplannerbackend.integration.nolio.write;

import com.koval.trainingplannerbackend.integration.nolio.NolioSports;
import com.koval.trainingplannerbackend.training.model.BlockType;
import com.koval.trainingplannerbackend.training.model.SportType;
import com.koval.trainingplannerbackend.training.model.Training;
import com.koval.trainingplannerbackend.training.model.WorkoutElement;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.StringJoiner;

/**
 * Maps a Training + its WorkoutElement tree to Nolio's planned-training payload
 * (https://github.com/NolioApp/NolioAPI-Documentation/wiki/Structured-Workout).
 *
 * Intensities are stored as % of FTP / threshold pace / CSS. For cycling with a
 * known FTP they are converted to absolute watts ({@code target_value_min/max});
 * otherwise the percent bounds ({@code step_percent_low/high}) are sent as-is.
 */
@Component
public class NolioWorkoutMapper {

    public Map<String, Object> toPayload(Training training, long idPartner, Integer ftp) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("id_partner", idPartner);
        payload.put("sport_id", NolioSports.toNolioSportId(training.getSportType()));
        payload.put("name", training.getTitle());
        payload.put("date_start", plannedDate(training).toString());
        if (training.getDescription() != null && !training.getDescription().isBlank()) {
            payload.put("description", training.getDescription());
        }
        if (training.getEstimatedDurationSeconds() != null) {
            payload.put("duration", training.getEstimatedDurationSeconds());
        }
        Integer distanceKm = toWholeKilometers(training.getEstimatedDistance());
        if (distanceKm != null) {
            payload.put("distance", distanceKm);
        }
        if (training.getBlocks() != null && !training.getBlocks().isEmpty()) {
            TargetContext ctx = new TargetContext(training.getSportType(), ftp);
            payload.put("structured_workout", mapElements(training.getBlocks(), ctx));
        }
        return payload;
    }

    /**
     * Nolio requires a calendar date for planned trainings. Library trainings have
     * no scheduled date, so the creation date is used: identical to "today" for
     * auto-synced new trainings, and stable across later update pushes.
     */
    private LocalDate plannedDate(Training training) {
        return training.getCreatedAt() != null
                ? training.getCreatedAt().toLocalDate()
                : LocalDate.now();
    }

    private Integer toWholeKilometers(Integer meters) {
        if (meters == null || meters <= 0) return null;
        int km = (int) Math.round(meters / 1000.0);
        return km > 0 ? km : null;
    }

    private List<Map<String, Object>> mapElements(List<WorkoutElement> elements, TargetContext ctx) {
        List<Map<String, Object>> out = new ArrayList<>(elements.size());
        for (WorkoutElement element : elements) {
            out.add(mapElement(element, ctx));
        }
        return out;
    }

    private Map<String, Object> mapElement(WorkoutElement element, TargetContext ctx) {
        if (element.isSet()) {
            return mapRepetition(element, ctx);
        }
        return mapStep(element, ctx);
    }

    private Map<String, Object> mapRepetition(WorkoutElement set, TargetContext ctx) {
        Map<String, Object> repetition = new LinkedHashMap<>();
        repetition.put("type", "repetition");
        repetition.put("value", set.repetitions() != null ? set.repetitions() : 1);
        List<Map<String, Object>> steps = mapElements(set.elements(), ctx);
        if (set.restDurationSeconds() != null && set.restDurationSeconds() > 0) {
            steps.add(restStep(set.restDurationSeconds(), set.restIntensity(), ctx));
        }
        repetition.put("steps", steps);
        return repetition;
    }

    private Map<String, Object> restStep(int durationSeconds, Integer restIntensity, TargetContext ctx) {
        Map<String, Object> step = new LinkedHashMap<>();
        step.put("type", "step");
        step.put("intensity_type", "rest");
        step.put("step_duration_type", "duration");
        step.put("step_duration_value", durationSeconds);
        if (restIntensity != null && restIntensity > 0) {
            putTarget(step, restIntensity, restIntensity, ctx);
        } else {
            step.put("target_type", "no_target");
        }
        return step;
    }

    private Map<String, Object> mapStep(WorkoutElement element, TargetContext ctx) {
        Map<String, Object> step = new LinkedHashMap<>();
        step.put("type", "step");
        step.put("intensity_type", mapIntensityType(element));

        if (element.distanceMeters() != null) {
            step.put("step_duration_type", "distance");
            step.put("step_duration_value", element.distanceMeters());
        } else if (element.durationSeconds() != null) {
            step.put("step_duration_type", "duration");
            step.put("step_duration_value", element.durationSeconds());
        }
        if (element.type() == BlockType.FREE) {
            step.put("open_duration", true);
        }

        if (element.type() == BlockType.RAMP
                && element.intensityStart() != null
                && element.intensityEnd() != null) {
            putTarget(step, element.intensityStart(), element.intensityEnd(), ctx);
        } else if (element.intensityTarget() != null) {
            putTarget(step, element.intensityTarget(), element.intensityTarget(), ctx);
        } else {
            step.put("target_type", "no_target");
        }

        if (element.cadenceTarget() != null) {
            step.put("secondary_step", cadenceStep(element.cadenceTarget()));
        }

        String comment = buildComment(element);
        if (comment != null) {
            step.put("comment", comment);
        }
        return step;
    }

    /**
     * Emits the intensity target: absolute watts when the sport is power-based and
     * FTP is known, percent bounds otherwise. {@code low}/{@code high} may be equal
     * (fixed target) or differ (ramp).
     */
    private void putTarget(Map<String, Object> step, int lowPercent, int highPercent, TargetContext ctx) {
        step.put("target_type", ctx.targetType());
        if (ctx.usesWatts()) {
            int wattsLow = Math.round(lowPercent * ctx.ftp() / 100f);
            int wattsHigh = Math.round(highPercent * ctx.ftp() / 100f);
            step.put("target_value_min", Math.min(wattsLow, wattsHigh));
            step.put("target_value_max", Math.max(wattsLow, wattsHigh));
        } else {
            step.put("step_percent_low", Math.min(lowPercent, highPercent));
            step.put("step_percent_high", Math.max(lowPercent, highPercent));
        }
    }

    private Map<String, Object> cadenceStep(int cadence) {
        Map<String, Object> secondary = new LinkedHashMap<>();
        secondary.put("type", "step");
        secondary.put("target_type", "cadence");
        secondary.put("target_value_max", cadence);
        return secondary;
    }

    private String mapIntensityType(WorkoutElement element) {
        BlockType type = element.type();
        if (type == null) return "active";
        return switch (type) {
            case WARMUP -> "warmup";
            case COOLDOWN -> "cooldown";
            case PAUSE, TRANSITION -> "rest";
            case RAMP -> isRampDown(element) ? "ramp_down" : "ramp_up";
            case INTERVAL, STEADY, FREE -> "active";
        };
    }

    private boolean isRampDown(WorkoutElement element) {
        return element.intensityStart() != null
                && element.intensityEnd() != null
                && element.intensityEnd() < element.intensityStart();
    }

    /** Carries everything Nolio's format has no field for: label, notes, zone, stroke. */
    private String buildComment(WorkoutElement element) {
        StringJoiner joiner = new StringJoiner("\n");
        if (element.label() != null && !element.label().isBlank()) {
            joiner.add(element.label());
        }
        if (element.description() != null && !element.description().isBlank()) {
            joiner.add(element.description());
        }
        String zone = element.zoneLabel() != null ? element.zoneLabel() : element.zoneTarget();
        if (zone != null && !zone.isBlank()) {
            joiner.add("Zone: " + zone);
        }
        if (element.strokeType() != null) {
            joiner.add("Stroke: " + element.strokeType().name());
        }
        if (element.equipment() != null && !element.equipment().isEmpty()) {
            joiner.add("Equipment: " + String.join(", ",
                    element.equipment().stream().map(Enum::name).toList()));
        }
        return joiner.length() > 0 ? joiner.toString() : null;
    }

    /** Sport-dependent target emission: cycling pushes watts when FTP is known. */
    private record TargetContext(SportType sport, Integer ftp) {

        String targetType() {
            if (sport == SportType.RUNNING || sport == SportType.SWIMMING) return "pace";
            return "power";
        }

        boolean usesWatts() {
            return "power".equals(targetType()) && ftp != null && ftp > 0;
        }
    }
}
