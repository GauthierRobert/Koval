package com.koval.trainingplannerbackend.integration.sync.events;

import com.koval.trainingplannerbackend.integration.sync.WorkoutSyncSourceType;

/**
 * Workout was rescheduled or its underlying training changed — providers should update
 * the existing remote entry (or create one if none exists yet).
 */
public record WorkoutSyncUpdatedEvent(
        String athleteId,
        WorkoutSyncSourceType sourceType,
        String sourceId) implements WorkoutSyncEvent {}
