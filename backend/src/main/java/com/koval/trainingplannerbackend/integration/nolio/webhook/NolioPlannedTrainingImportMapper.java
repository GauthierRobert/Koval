package com.koval.trainingplannerbackend.integration.nolio.webhook;

import com.fasterxml.jackson.databind.JsonNode;
import com.koval.trainingplannerbackend.integration.nolio.NolioSports;
import com.koval.trainingplannerbackend.training.model.BlockType;
import com.koval.trainingplannerbackend.training.model.CyclingTraining;
import com.koval.trainingplannerbackend.training.model.RunningTraining;
import com.koval.trainingplannerbackend.training.model.SportType;
import com.koval.trainingplannerbackend.training.model.SwimmingTraining;
import com.koval.trainingplannerbackend.training.model.Training;
import com.koval.trainingplannerbackend.training.model.WorkoutElement;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.StringJoiner;

/**
 * Maps a planned workout from the direct Nolio API ({@code /get/planned/training/})
 * to a {@link Training}. The inverse of {@code NolioWorkoutMapper}: Nolio targets
 * are absolute (watts, m/s, bpm) — power converts to % of FTP when the athlete's
 * FTP is known, other target kinds are preserved as human-readable block notes.
 * Returns null for sports we have no Training subclass for.
 */
@Component
public class NolioPlannedTrainingImportMapper {

    public Training map(JsonNode planned, Integer ftp) {
        SportType sport = NolioSports.fromNolioSportId(planned.path("sport_id").asInt(-1));
        if (sport == null) {
            return null;
        }

        Training training = newTrainingFor(sport);
        if (training == null) {
            return null;
        }
        training.setTitle(planned.path("name").asText("Nolio workout"));
        String description = planned.path("description").asText("");
        if (!description.isBlank()) {
            training.setDescription(description);
        }
        int duration = planned.path("duration").asInt(0);
        if (duration > 0) {
            training.setEstimatedDurationSeconds(duration);
        }
        double distanceKm = planned.path("distance").asDouble(0);
        if (distanceKm > 0) {
            training.setEstimatedDistance((int) Math.round(distanceKm * 1000));
        }
        double loadCoggan = planned.path("load_coggan").asDouble(0);
        if (loadCoggan > 0) {
            training.setEstimatedTss((int) Math.round(loadCoggan));
        }

        JsonNode structured = planned.path("structured_workout");
        if (structured.isArray() && !structured.isEmpty()) {
            training.setBlocks(mapSteps(structured, ftp));
        }
        return training;
    }

    private Training newTrainingFor(SportType sport) {
        return switch (sport) {
            case CYCLING -> new CyclingTraining();
            case RUNNING -> new RunningTraining();
            case SWIMMING -> new SwimmingTraining();
            case BRICK -> null; // never produced by NolioSports.fromNolioSportId
        };
    }

    private List<WorkoutElement> mapSteps(JsonNode steps, Integer ftp) {
        List<WorkoutElement> out = new ArrayList<>(steps.size());
        for (JsonNode step : steps) {
            out.add("repetition".equals(step.path("type").asText())
                    ? mapRepetition(step, ftp)
                    : mapStep(step, ftp));
        }
        return out;
    }

    private WorkoutElement mapRepetition(JsonNode repetition, Integer ftp) {
        int value = Math.max(1, repetition.path("value").asInt(1));
        List<WorkoutElement> children = mapSteps(repetition.path("steps"), ftp);
        return new WorkoutElement(value, children, null, null,
                null, null, null, null, null, null, null, null, null, null, null,
                null, null, null, null);
    }

    private WorkoutElement mapStep(JsonNode step, Integer ftp) {
        Target target = Target.of(step, ftp);
        BlockType type = blockType(step, target);

        Integer durationSeconds = null;
        Integer distanceMeters = null;
        int durationValue = step.path("step_duration_value").asInt(0);
        if (durationValue > 0) {
            if ("distance".equals(step.path("step_duration_type").asText())) {
                distanceMeters = durationValue;
            } else {
                durationSeconds = durationValue;
            }
        }

        Integer intensityTarget = null;
        Integer intensityStart = null;
        Integer intensityEnd = null;
        if (type == BlockType.RAMP && target.percentLow != null && target.percentHigh != null) {
            boolean down = "ramp_down".equals(step.path("intensity_type").asText());
            intensityStart = down ? target.percentHigh : target.percentLow;
            intensityEnd = down ? target.percentLow : target.percentHigh;
        } else if (target.percent() != null) {
            intensityTarget = target.percent();
        }

        return new WorkoutElement(null, null, null, null,
                type, durationSeconds, distanceMeters, label(type), stepDescription(step, target),
                intensityTarget, intensityStart, intensityEnd, null, null, null,
                null, null, null, null);
    }

    private BlockType blockType(JsonNode step, Target target) {
        return switch (step.path("intensity_type").asText("")) {
            case "warmup" -> BlockType.WARMUP;
            case "cooldown" -> BlockType.COOLDOWN;
            case "ramp_up", "ramp_down" -> BlockType.RAMP;
            // Rest with a target = active recovery; without = full pause.
            case "rest" -> target.hasAnyTarget ? BlockType.STEADY : BlockType.PAUSE;
            default -> BlockType.STEADY;
        };
    }

    private static String label(BlockType type) {
        return switch (type) {
            case WARMUP -> "Warmup";
            case COOLDOWN -> "Cooldown";
            case RAMP -> "Ramp";
            case PAUSE -> "Rest";
            default -> "Steady";
        };
    }

    /** Comment + any target we could not express as a % intensity. */
    private static String stepDescription(JsonNode step, Target target) {
        StringJoiner joiner = new StringJoiner("\n");
        if (target.displayText != null) {
            joiner.add(target.displayText);
        }
        String comment = step.path("comment").asText("");
        if (!comment.isBlank()) {
            joiner.add(comment);
        }
        return joiner.length() > 0 ? joiner.toString() : null;
    }

    /**
     * A step's resolved target: percent bounds when convertible (power + FTP, or
     * Nolio's percent fields), otherwise a display string for the block notes.
     */
    private record Target(Integer percentLow, Integer percentHigh, String displayText, boolean hasAnyTarget) {

        Integer percent() {
            if (percentLow == null && percentHigh == null) return null;
            if (percentLow == null) return percentHigh;
            if (percentHigh == null) return percentLow;
            return Math.round((percentLow + percentHigh) / 2f);
        }

        static Target of(JsonNode step, Integer ftp) {
            // Partner-created objects may echo our percent fields back — use them directly.
            if (step.hasNonNull("step_percent_low") || step.hasNonNull("step_percent_high")) {
                Integer low = step.hasNonNull("step_percent_low") ? step.get("step_percent_low").asInt() : null;
                Integer high = step.hasNonNull("step_percent_high") ? step.get("step_percent_high").asInt() : null;
                return new Target(low, high, null, true);
            }

            String targetType = step.path("target_type").asText("");
            Double min = step.hasNonNull("target_value_min") ? step.get("target_value_min").asDouble() : null;
            Double max = step.hasNonNull("target_value_max") ? step.get("target_value_max").asDouble() : null;
            if ((min == null && max == null) || "no_target".equals(targetType) || targetType.isBlank()) {
                return new Target(null, null, null, false);
            }

            if ("power".equals(targetType) && ftp != null && ftp > 0) {
                Integer low = min != null ? Math.toIntExact(Math.round(min / ftp * 100)) : null;
                Integer high = max != null ? Math.toIntExact(Math.round(max / ftp * 100)) : null;
                return new Target(low, high, null, true);
            }
            return new Target(null, null, displayText(targetType, min, max), true);
        }

        private static String displayText(String targetType, Double min, Double max) {
            return switch (targetType) {
                case "power" -> "Power " + range(min, max, 1, "W");
                case "heartrate" -> "HR " + range(min, max, 1, "bpm");
                case "speed" -> "Speed " + range(min == null ? null : min * 3.6, max == null ? null : max * 3.6, 1, "km/h");
                case "pace" -> "Pace " + paceRange(min, max);
                default -> targetType + " " + range(min, max, 1, "");
            };
        }

        private static String range(Double min, Double max, double scale, String unit) {
            String suffix = unit.isBlank() ? "" : " " + unit;
            if (min == null) return Math.round(max * scale) + suffix;
            if (max == null) return Math.round(min * scale) + suffix;
            return Math.round(min * scale) + "–" + Math.round(max * scale) + suffix;
        }

        /** Nolio pace is m/s; faster bound first, formatted as min/km. */
        private static String paceRange(Double minMps, Double maxMps) {
            if (minMps == null) return formatPace(maxMps) + " /km";
            if (maxMps == null) return formatPace(minMps) + " /km";
            return formatPace(Math.max(minMps, maxMps)) + "–" + formatPace(Math.min(minMps, maxMps)) + " /km";
        }

        private static String formatPace(double mps) {
            if (mps <= 0) return "?";
            int secondsPerKm = (int) Math.round(1000 / mps);
            return "%d:%02d".formatted(secondsPerKm / 60, secondsPerKm % 60);
        }
    }
}
