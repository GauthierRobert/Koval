package com.koval.trainingplannerbackend.training.received;

import com.koval.trainingplannerbackend.training.model.SportType;
import com.koval.trainingplannerbackend.training.model.TrainingType;

import java.time.LocalDateTime;

public record ReceivedTrainingResponse(
        String id,
        String trainingId,
        String title,
        String description,
        SportType sportType,
        TrainingType trainingType,
        Integer estimatedTss,
        Double estimatedIf,
        Integer estimatedDurationSeconds,
        String assignedByName,
        ReceivedTrainingOrigin origin,
        String originName,
        LocalDateTime receivedAt
) {}
