package com.koval.trainingplannerbackend.training.received;

import java.time.LocalDateTime;

public record ReceivedTrainingResponse(
        String id,
        String trainingId,
        String assignedByName,
        ReceivedTrainingOrigin origin,
        String originName,
        LocalDateTime receivedAt
) {}
