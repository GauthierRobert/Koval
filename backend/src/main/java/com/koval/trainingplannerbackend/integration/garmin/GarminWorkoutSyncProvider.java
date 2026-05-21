package com.koval.trainingplannerbackend.integration.garmin;

import com.koval.trainingplannerbackend.auth.User;
import com.koval.trainingplannerbackend.integration.sync.WorkoutSyncPayload;
import com.koval.trainingplannerbackend.integration.sync.WorkoutSyncProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * Garmin Connect adapter for the auto-sync framework. Opt-in via {@code user.garminAutoPushWorkouts}.
 */
@Component
public class GarminWorkoutSyncProvider implements WorkoutSyncProvider {

    private static final Logger log = LoggerFactory.getLogger(GarminWorkoutSyncProvider.class);

    private final GarminTrainingTargetService trainingTargetService;

    public GarminWorkoutSyncProvider(GarminTrainingTargetService trainingTargetService) {
        this.trainingTargetService = trainingTargetService;
    }

    @Override
    public String providerId() {
        return "garmin";
    }

    @Override
    public boolean isEnabled(User athlete) {
        return Boolean.TRUE.equals(athlete.getGarminAutoPushWorkouts())
                && athlete.getGarminAccessToken() != null
                && athlete.getGarminAccessTokenSecret() != null;
    }

    @Override
    public Optional<String> push(User athlete, WorkoutSyncPayload payload) {
        if (payload.training() == null || payload.scheduledDate() == null) return Optional.empty();
        try {
            return trainingTargetService.pushTraining(athlete, payload.training(), payload.scheduledDate());
        } catch (RuntimeException e) {
            log.warn("Garmin push failed for athlete {} ({}/{}): {}",
                    athlete.getId(), payload.sourceType(), payload.sourceId(), e.getMessage());
            return Optional.empty();
        }
    }

    @Override
    public Optional<String> update(User athlete, WorkoutSyncPayload payload, String externalRef) {
        if (payload.training() == null || payload.scheduledDate() == null) return Optional.empty();
        try {
            return trainingTargetService.updateTraining(
                    athlete, payload.training(), payload.scheduledDate(), externalRef);
        } catch (RuntimeException e) {
            log.warn("Garmin update failed for athlete {} ({}/{}): {}",
                    athlete.getId(), payload.sourceType(), payload.sourceId(), e.getMessage());
            return Optional.empty();
        }
    }

    @Override
    public void delete(User athlete, WorkoutSyncPayload payload, String externalRef) {
        try {
            trainingTargetService.deleteTraining(athlete, externalRef);
        } catch (RuntimeException e) {
            log.warn("Garmin delete failed for ref {}: {}", externalRef, e.getMessage());
        }
    }
}
