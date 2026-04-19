package com.koval.trainingplannerbackend.integration.nolio.write;

import com.koval.trainingplannerbackend.training.model.BlockType;
import com.koval.trainingplannerbackend.training.model.Training;
import com.koval.trainingplannerbackend.training.model.WorkoutElement;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Maps a Training + its WorkoutElement tree to the Nolio workout JSON payload.
 *
 * The payload shape below follows the generic "structured workout" pattern used
 * by most training platforms. Exact field names should be verified against
 * Nolio's developer portal before going to prod.
 */
@Component
public class NolioWorkoutMapper {

    public Map<String, Object> toPayload(Training training) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("name", training.getTitle());
        if (training.getDescription() != null) {
            payload.put("description", training.getDescription());
        }
        if (training.getSportType() != null) {
            payload.put("sport", training.getSportType().name());
        }
        if (training.getEstimatedDurationSeconds() != null) {
            payload.put("estimatedDurationSeconds", training.getEstimatedDurationSeconds());
        }
        if (training.getEstimatedTss() != null) {
            payload.put("estimatedTss", training.getEstimatedTss());
        }
        payload.put("steps", training.getBlocks() == null
                ? List.of()
                : mapElements(training.getBlocks()));
        return payload;
    }

    private List<Map<String, Object>> mapElements(List<WorkoutElement> elements) {
        List<Map<String, Object>> out = new ArrayList<>(elements.size());
        for (WorkoutElement element : elements) {
            out.add(mapElement(element));
        }
        return out;
    }

    private Map<String, Object> mapElement(WorkoutElement element) {
        if (element.isSet()) {
            Map<String, Object> set = new LinkedHashMap<>();
            set.put("type", "REPEAT");
            set.put("repetitions", element.repetitions() != null ? element.repetitions() : 1);
            set.put("steps", mapElements(element.elements()));
            if (element.restDurationSeconds() != null && element.restDurationSeconds() > 0) {
                set.put("restDurationSeconds", element.restDurationSeconds());
                if (element.restIntensity() != null) {
                    set.put("restIntensityPercent", element.restIntensity());
                }
            }
            return set;
        }

        Map<String, Object> step = new LinkedHashMap<>();
        step.put("type", mapLeafType(element.type()));
        if (element.label() != null) {
            step.put("label", element.label());
        }
        if (element.description() != null) {
            step.put("description", element.description());
        }
        if (element.durationSeconds() != null) {
            step.put("durationSeconds", element.durationSeconds());
        }
        if (element.distanceMeters() != null) {
            step.put("distanceMeters", element.distanceMeters());
        }

        if (element.type() == BlockType.RAMP
                && element.intensityStart() != null
                && element.intensityEnd() != null) {
            step.put("intensityStartPercent", element.intensityStart());
            step.put("intensityEndPercent", element.intensityEnd());
        } else if (element.intensityTarget() != null) {
            step.put("intensityPercent", element.intensityTarget());
        }

        if (element.cadenceTarget() != null) {
            step.put("cadenceTarget", element.cadenceTarget());
        }
        if (element.zoneTarget() != null) {
            step.put("zone", element.zoneTarget());
        }
        if (element.strokeType() != null) {
            step.put("strokeType", element.strokeType().name());
        }
        if (element.equipment() != null && !element.equipment().isEmpty()) {
            step.put("equipment", element.equipment().stream().map(Enum::name).toList());
        }
        if (element.sendOffSeconds() != null) {
            step.put("sendOffSeconds", element.sendOffSeconds());
        }
        if (element.transitionType() != null) {
            step.put("transitionType", element.transitionType().name());
        }
        return step;
    }

    private String mapLeafType(BlockType type) {
        if (type == null) return "STEADY";
        return switch (type) {
            case WARMUP -> "WARMUP";
            case COOLDOWN -> "COOLDOWN";
            case INTERVAL -> "INTERVAL";
            case STEADY -> "STEADY";
            case RAMP -> "RAMP";
            case FREE -> "FREE";
            case PAUSE -> "REST";
            case TRANSITION -> "TRANSITION";
        };
    }
}
