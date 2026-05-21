package com.koval.trainingplannerbackend.integration.sync;

public enum WorkoutSyncSourceType {
    /** A personal scheduled workout (self-scheduled, coach-assigned, or club-assigned). */
    SCHEDULED_WORKOUT,
    /** A club training session the athlete has joined that has a linked training. */
    CLUB_SESSION
}
