package com.koval.trainingplannerbackend.training.tools;

import com.koval.trainingplannerbackend.training.model.BrickTraining;
import com.koval.trainingplannerbackend.training.model.CyclingTraining;
import com.koval.trainingplannerbackend.training.model.RunningTraining;
import com.koval.trainingplannerbackend.training.model.SwimmingTraining;
import com.koval.trainingplannerbackend.training.model.Training;
import com.koval.trainingplannerbackend.training.model.TrainingType;
import com.koval.trainingplannerbackend.training.model.WorkoutBlock;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.ArrayList;

@Component
public class TrainingMapper {

    /**
     * Convertit une requête DTO simplifiée (provenant de l'IA) en une entité Training riche.
     */
    public Training mapToEntity(TrainingRequest request) {
        Training training = createInstance(request.sportType());

        // 2. Champs de base
        training.setTitle(request.title());
        training.setDescription(request.description());
        
        // Gestion des tags (liste vide par défaut si null)
        training.setTags(request.tags() != null ? request.tags() : new ArrayList<>());

        // 3. Enums (Gestion sécurisée)
        if (request.trainingType() != null) {
            try {
                training.setTrainingType(TrainingType.valueOf(request.trainingType().toUpperCase()));
            } catch (IllegalArgumentException e) {
                // Fallback par défaut si l'IA invente un type
                training.setTrainingType(TrainingType.MIXED);
            }
        } else {
            training.setTrainingType(TrainingType.MIXED);
        }

        if (request.estimatedTss() != null) {
            training.setEstimatedTss(request.estimatedTss());
        }

        // 4. Mapping des Blocs + Calcul de la durée totale
        if (request.blocks() != null && !request.blocks().isEmpty()) {

            training.setBlocks(request.blocks());

            // Calcul automatique de la durée totale (somme des blocs)
            int totalDuration = request.blocks().stream()
                    .mapToInt(WorkoutBlock::durationSeconds)
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