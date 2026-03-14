package com.koval.trainingplannerbackend.training.tools;

import com.koval.trainingplannerbackend.training.model.BrickTraining;
import com.koval.trainingplannerbackend.training.model.CyclingTraining;
import com.koval.trainingplannerbackend.training.model.RunningTraining;
import com.koval.trainingplannerbackend.training.model.SportType;
import com.koval.trainingplannerbackend.training.model.SwimmingTraining;
import com.koval.trainingplannerbackend.training.model.Training;
import com.koval.trainingplannerbackend.training.model.TrainingType;
import com.koval.trainingplannerbackend.training.model.WorkoutBlock;
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
            List<WorkoutBlock> blocks = request.blocks().stream()
                    .map(b -> enforceDistanceXorDuration(b, sport))
                    .toList();
            training.setBlocks(blocks);

            int totalDuration = blocks.stream()
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
     * Enforces that each block has exactly one of durationSeconds or distanceMeters.
     * If both are provided, the preferred dimension per sport is kept and the other extrapolated.
     * If only one is provided, the other is extrapolated from typical sport speeds.
     *
     * Typical speeds used for extrapolation:
     *   CYCLING:  ~30 km/h  (8.33 m/s)
     *   RUNNING:  ~12 km/h  (3.33 m/s)
     *   SWIMMING: ~3.6 km/h (1.00 m/s)
     */
    private WorkoutBlock enforceDistanceXorDuration(WorkoutBlockRequest b, String sport) {
        Integer dur = b.dur();
        Integer dist = b.dist();

        boolean hasDur = dur != null && dur > 0;
        boolean hasDist = dist != null && dist > 0;

        SportType sportType = parseSportType(sport);
        double metersPerSecond = sportType.getTypicalSpeedMps();

        if (hasDur && hasDist) {
            // Both set — keep the primary dimension per sport, extrapolate the other.
            // For swimming/running (distance-based sports) prefer distance; for cycling prefer duration.
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

        return new WorkoutBlock(b.type(), dur, dist, b.label(), b.pct(), b.pctFrom(), b.pctTo(), b.cad(), b.zone(), null);
    }

    /**
     * Creates the sport-specific Training subclass based on the sport type string.
     */
    private Training createInstance(String sportType) {
        return switch (parseSportType(sportType)) {
            case RUNNING -> new RunningTraining();
            case SWIMMING -> new SwimmingTraining();
            case BRICK -> new BrickTraining();
            case CYCLING -> new CyclingTraining();
        };
    }

    private SportType parseSportType(String sportType) {
        if (sportType == null) return SportType.CYCLING;
        try {
            return SportType.valueOf(sportType.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return SportType.CYCLING;
        }
    }

}
