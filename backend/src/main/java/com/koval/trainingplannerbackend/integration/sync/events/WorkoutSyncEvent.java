package com.koval.trainingplannerbackend.integration.sync.events;

import com.koval.trainingplannerbackend.integration.sync.WorkoutSyncSourceType;

/**
 * Sealed event hierarchy published by schedule mutation services (ScheduleService,
 * CoachService, SessionParticipationService, SessionTrainingLinkService) and consumed
 * asynchronously by {@code WorkoutSyncDispatcher}.
 *
 * <p>Pairing every mutation with a typed event keeps the publishing services decoupled
 * from the integration layer — they don't know that Polar or Garmin exist.
 */
public sealed interface WorkoutSyncEvent
        permits WorkoutSyncCreatedEvent,
                WorkoutSyncUpdatedEvent,
                WorkoutSyncCancelledEvent {

    /** Athlete whose external calendar should be touched. */
    String athleteId();

    /** Discriminator for personal scheduled workout vs. joined club session. */
    WorkoutSyncSourceType sourceType();

    /** ScheduledWorkout id, or "{sessionId}:{athleteId}" composite for CLUB_SESSION sources. */
    String sourceId();
}
