package com.koval.trainingplannerbackend.integration.polar;

import com.koval.trainingplannerbackend.auth.User;
import com.koval.trainingplannerbackend.integration.sync.WorkoutSyncPayload;
import com.koval.trainingplannerbackend.integration.sync.WorkoutSyncProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * Polar Flow adapter for the auto-sync framework. Delegates to {@link PolarTrainingTargetService}
 * for the mapping + API call. Opt-in via {@code user.polarAutoPushWorkouts}.
 */
@Component
public class PolarWorkoutSyncProvider implements WorkoutSyncProvider {

    private static final Logger log = LoggerFactory.getLogger(PolarWorkoutSyncProvider.class);

    private final PolarTrainingTargetService trainingTargetService;

    public PolarWorkoutSyncProvider(PolarTrainingTargetService trainingTargetService) {
        this.trainingTargetService = trainingTargetService;
    }

    @Override
    public String providerId() {
        return "polar";
    }

    @Override
    public boolean isEnabled(User athlete) {
        return Boolean.TRUE.equals(athlete.getPolarAutoPushWorkouts())
                && athlete.getPolarUserId() != null
                && athlete.getPolarAccessToken() != null;
    }

    @Override
    public Optional<String> push(User athlete, WorkoutSyncPayload payload) {
        if (payload.training() == null || payload.scheduledDate() == null) return Optional.empty();
        try {
            return Optional.ofNullable(trainingTargetService.pushTraining(
                    athlete, payload.training(), payload.scheduledDate()));
        } catch (RuntimeException e) {
            log.warn("Polar push failed for athlete {} ({}/{}): {}",
                    athlete.getId(), payload.sourceType(), payload.sourceId(), e.getMessage());
            return Optional.empty();
        }
    }

    @Override
    public void delete(User athlete, WorkoutSyncPayload payload, String externalRef) {
        try {
            trainingTargetService.deleteTrainingTarget(athlete, externalRef);
        } catch (RuntimeException e) {
            log.warn("Polar delete failed for training target {}: {}", externalRef, e.getMessage());
        }
    }
}
