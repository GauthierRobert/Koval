package com.koval.trainingplannerbackend.integration.sync;

import com.koval.trainingplannerbackend.auth.User;

import java.util.Optional;

/**
 * Strategy for syncing a planned workout to an external service that exposes a
 * scheduled-workout / training-target / calendar entry concept (Polar Flow, Garmin Connect, ...).
 *
 * <p>Implementations are auto-discovered as Spring beans by {@link WorkoutSyncDispatcher} and
 * fan-out happens asynchronously off the schedule mutation thread. Failures must NOT propagate
 * — they should be returned as empty {@link Optional} (and logged inside the provider).
 *
 * <p>A provider is consulted only when {@link #isEnabled(User)} returns {@code true} — typically
 * "user has connected the provider AND has the per-provider auto-push toggle on".
 */
public interface WorkoutSyncProvider {

    /** Stable, lowercase id (e.g. {@code "polar"}, {@code "garmin"}). Used as the record key. */
    String providerId();

    /** True when the athlete has both connected the provider and opted in to auto-push. */
    boolean isEnabled(User athlete);

    /**
     * Create the workout on the provider. Returns the provider-assigned external id (training
     * target id, workout schedule id, ...) — empty when the provider didn't echo one or the
     * call failed.
     */
    Optional<String> push(User athlete, WorkoutSyncPayload payload);

    /**
     * Update an already-pushed workout. Default implementation deletes then re-creates, which
     * fits providers without a native update endpoint. Providers with patch support should
     * override and return the (possibly new) external ref.
     */
    default Optional<String> update(User athlete, WorkoutSyncPayload payload, String externalRef) {
        delete(athlete, payload, externalRef);
        return push(athlete, payload);
    }

    /** Best-effort delete on the provider. Errors should be swallowed and logged inside. */
    void delete(User athlete, WorkoutSyncPayload payload, String externalRef);
}
