package com.koval.trainingplannerbackend.training.tools;

import com.koval.trainingplannerbackend.training.model.BrickTraining;
import com.koval.trainingplannerbackend.training.model.CyclingTraining;
import com.koval.trainingplannerbackend.training.model.RunningTraining;
import com.koval.trainingplannerbackend.training.model.SportType;
import com.koval.trainingplannerbackend.training.model.SwimmingTraining;
import com.koval.trainingplannerbackend.training.model.Training;
import com.koval.trainingplannerbackend.training.model.TrainingType;
import com.koval.trainingplannerbackend.training.model.WorkoutElement;
import com.koval.trainingplannerbackend.training.model.WorkoutElementFlattener;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
public class TrainingMapper {

    /**
     * Converts a simplified DTO request (from the AI) into a rich Training entity.
     */
    public Training mapToEntity(TrainingRequest request) {
        Training training = createInstance(request.sport());

        // Base fields
        training.setTitle(request.title());
        training.setDescription(request.desc());

        // Groups (default to empty list if null)
        training.setGroupIds(request.tags() != null ? request.tags() : new ArrayList<>());

        // Enums (safe handling)
        if (request.type() != null) {
            try {
                training.setTrainingType(TrainingType.valueOf(request.type().toUpperCase()));
            } catch (IllegalArgumentException e) {
                // Fallback if the AI invents an unknown type
                training.setTrainingType(TrainingType.MIXED);
            }
        } else {
            training.setTrainingType(TrainingType.MIXED);
        }

        if (request.tss() != null) {
            training.setEstimatedTss(request.tss());
        }

        // Block mapping + total duration calculation
        if (request.blocks() != null && !request.blocks().isEmpty()) {
            String sport = request.sport() != null ? request.sport().toUpperCase() : "CYCLING";
            List<WorkoutElement> blocks = request.blocks().stream()
                    .map(b -> mapElement(b, sport))
                    .toList();
            training.setBlocks(blocks);

            // Flatten to compute total duration
            List<WorkoutElement> flat = WorkoutElementFlattener.flatten(blocks);
            int totalDuration = flat.stream()
                    .mapToInt(wb -> wb.durationSeconds() != null ? wb.durationSeconds() : 0)
                    .sum();
            training.setEstimatedDurationSeconds(totalDuration);
        } else {
            training.setBlocks(new ArrayList<>());
            training.setEstimatedDurationSeconds(0);
        }

        return training;
    }

    /**
     * Recursively maps a WorkoutElementRequest to a WorkoutElement.
     * Sets map children recursively; leaves enforce distance-xor-duration.
     */
    private WorkoutElement mapElement(WorkoutElementRequest b, String sport) {
        if (b.isSet()) {
            List<WorkoutElement> children = b.elements().stream()
                    .map(child -> mapElement(child, sport))
                    .toList();
            return new WorkoutElement(b.reps(), children, b.restDur(), b.restPct(),
                    null, null, null, null, null, null, null, null, null, null, null);
        }
        return enforceDistanceXorDuration(b, sport);
    }

    /**
     * Enforces that each leaf block has exactly one of durationSeconds or distanceMeters.
     */
    private WorkoutElement enforceDistanceXorDuration(WorkoutElementRequest b, String sport) {
        Integer dur = b.dur();
        Integer dist = b.dist();

        boolean hasDur = dur != null && dur > 0;
        boolean hasDist = dist != null && dist > 0;

        SportType sportType = SportType.fromString(sport);
        double metersPerSecond = sportType.getTypicalSpeedMps();

        if (hasDur && hasDist) {
            if ("RUNNING".equals(sport) || "SWIMMING".equals(sport)) {
                dur = (int) Math.round(dist / metersPerSecond);
            } else {
                dist = (int) Math.round(dur * metersPerSecond);
            }
        } else if (hasDur) {
            dist = (int) Math.round(dur * metersPerSecond);
        } else if (hasDist) {
            dur = (int) Math.round(dist / metersPerSecond);
        }

        return new WorkoutElement(null, null, null, null,
                b.type(), dur, dist, b.label(), b.desc(), b.pct(), b.pctFrom(), b.pctTo(), b.cad(), b.zone(), null);
    }

    /**
     * Creates the sport-specific Training subclass based on the sport type string.
     */
    private Training createInstance(String sportType) {
        return switch (SportType.fromString(sportType)) {
            case RUNNING -> new RunningTraining();
            case SWIMMING -> new SwimmingTraining();
            case BRICK -> new BrickTraining();
            case CYCLING -> new CyclingTraining();
        };
    }

}
