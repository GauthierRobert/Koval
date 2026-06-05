package com.koval.trainingplannerbackend.integration.suunto;

import com.koval.trainingplannerbackend.auth.User;
import com.koval.trainingplannerbackend.integration.sync.WorkoutSyncPayload;
import com.koval.trainingplannerbackend.integration.sync.WorkoutSyncProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * Suunto adapter for the auto-sync framework: planned workouts land on the watch as
 * SuuntoPlus guides. Opt-in via {@code user.suuntoAutoPushWorkouts}.
 */
@Component
public class SuuntoWorkoutSyncProvider implements WorkoutSyncProvider {

    private static final Logger log = LoggerFactory.getLogger(SuuntoWorkoutSyncProvider.class);

    private final SuuntoGuideService guideService;

    public SuuntoWorkoutSyncProvider(SuuntoGuideService guideService) {
        this.guideService = guideService;
    }

    @Override
    public String providerId() {
        return "suunto";
    }

    @Override
    public boolean isEnabled(User athlete) {
        return Boolean.TRUE.equals(athlete.getSuuntoAutoPushWorkouts())
                && athlete.getSuuntoAccessToken() != null
                && athlete.getSuuntoUserId() != null;
    }

    @Override
    public Optional<String> push(User athlete, WorkoutSyncPayload payload) {
        if (payload.training() == null || payload.scheduledDate() == null) return Optional.empty();
        try {
            return guideService.pushTraining(athlete, payload.training(), payload.scheduledDate(),
                    payload.sourceId());
        } catch (RuntimeException e) {
            log.warn("Suunto push failed for athlete {} ({}/{}): {}",
                    athlete.getId(), payload.sourceType(), payload.sourceId(), e.getMessage());
            return Optional.empty();
        }
    }

    @Override
    public Optional<String> update(User athlete, WorkoutSyncPayload payload, String externalRef) {
        if (payload.training() == null || payload.scheduledDate() == null) return Optional.empty();
        try {
            return guideService.updateTraining(athlete, payload.training(), payload.scheduledDate(),
                    payload.sourceId(), externalRef);
        } catch (RuntimeException e) {
            log.warn("Suunto update failed for athlete {} ({}/{}): {}",
                    athlete.getId(), payload.sourceType(), payload.sourceId(), e.getMessage());
            return Optional.empty();
        }
    }

    @Override
    public void delete(User athlete, WorkoutSyncPayload payload, String externalRef) {
        try {
            guideService.deleteTraining(athlete, externalRef);
        } catch (RuntimeException e) {
            log.warn("Suunto delete failed for ref {}: {}", externalRef, e.getMessage());
        }
    }
}
