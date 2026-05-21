package com.koval.trainingplannerbackend.integration.sync;

import com.koval.trainingplannerbackend.training.model.Training;

import java.time.LocalDate;

/**
 * Resolved data passed to a {@link WorkoutSyncProvider}: the planned training and the date it
 * should appear on in the athlete's external calendar. Also carries the source coordinates so
 * the provider can look up its existing external ref for update/delete.
 */
public record WorkoutSyncPayload(
        String athleteId,
        WorkoutSyncSourceType sourceType,
        String sourceId,
        Training training,
        LocalDate scheduledDate,
        String title,
        String notes) {
}
