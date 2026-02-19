package com.koval.trainingplannerbackend.training.tools;

import com.koval.trainingplannerbackend.training.model.BrickTraining;
import com.koval.trainingplannerbackend.training.model.CyclingTraining;
import com.koval.trainingplannerbackend.training.model.RunningTraining;
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
     * Convertit une requête DTO simplifiée (provenant de l'IA) en une entité Training riche.
     */
    public Training mapToEntity(TrainingRequest request) {
        Training training = createInstance(request.sport());

        // 2. Champs de base
        training.setTitle(request.title());
        training.setDescription(request.desc());
        
        // Gestion des tags (liste vide par défaut si null)
        training.setTags(request.tags() != null ? request.tags() : new ArrayList<>());

        // 3. Enums (Gestion sécurisée)
        if (request.type() != null) {
            try {
                training.setTrainingType(TrainingType.valueOf(request.type().toUpperCase()));
            } catch (IllegalArgumentException e) {
                // Fallback par défaut si l'IA invente un type
                training.setTrainingType(TrainingType.MIXED);
            }
        } else {
            training.setTrainingType(TrainingType.MIXED);
        }

        if (request.tss() != null) {
            training.setEstimatedTss(request.tss());
        }

        // 4. Mapping des Blocs + Calcul de la durée totale
        if (request.blocks() != null && !request.blocks().isEmpty()) {
            List<WorkoutBlock> blocks = request.blocks().stream()
                    .map(b -> new WorkoutBlock(b.type(), b.dur(), b.dist(), b.label(), b.pct(), b.pctFrom(), b.pctTo(), b.cad()))
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
     * Crée l'instance Java spécifique selon le type de sport string.
     */
    private Training createInstance(String sportType) {
        if (sportType == null) {
            return new CyclingTraining(); // Défaut
        }
        return switch (sportType.trim().toUpperCase()) {
            case "RUNNING" -> new RunningTraining();
            case "SWIMMING" -> new SwimmingTraining();
            case "BRICK" -> new BrickTraining();
            default -> new CyclingTraining();
        };
    }

}