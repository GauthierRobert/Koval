package com.koval.trainingplannerbackend.integration.sync.events;

import com.koval.trainingplannerbackend.integration.sync.WorkoutSyncSourceType;

/**
 * Workout was deleted, unassigned, or the athlete left a club session — providers should
 * remove any remote entry they had previously created for it.
 */
public record WorkoutSyncCancelledEvent(
        String athleteId,
        WorkoutSyncSourceType sourceType,
        String sourceId) implements WorkoutSyncEvent {}
