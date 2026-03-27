package com.koval.trainingplannerbackend.training.received;

import java.time.LocalDateTime;

/** API response DTO for a training received by an athlete, from either an explicit assignment or a club session. */
public record ReceivedTrainingResponse(
        String id,
        String trainingId,
        String assignedByName,
        ReceivedTrainingOrigin origin,
        String originName,
        LocalDateTime receivedAt
) {}
