package com.koval.trainingplannerbackend.integration.sync.events;

import com.koval.trainingplannerbackend.integration.sync.WorkoutSyncSourceType;

/** Workout was newly placed on the athlete's calendar — providers should create remote entries. */
public record WorkoutSyncCreatedEvent(
        String athleteId,
        WorkoutSyncSourceType sourceType,
        String sourceId) implements WorkoutSyncEvent {}
