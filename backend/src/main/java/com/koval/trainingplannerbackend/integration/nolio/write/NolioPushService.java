package com.koval.trainingplannerbackend.integration.nolio.write;

import com.koval.trainingplannerbackend.auth.User;
import com.koval.trainingplannerbackend.auth.UserService;
import com.koval.trainingplannerbackend.training.TrainingRepository;
import com.koval.trainingplannerbackend.training.model.Training;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * Orchestrates pushing a {@link Training} to the user's Nolio account:
 * sets the sync status, maps the payload, calls the API, and records the result.
 *
 * {@link #pushAsync} is invoked by the auto-sync hook in TrainingService so
 * CRUD latency isn't impacted by Nolio round-trips.
 */
@Service
public class NolioPushService {

    private static final Logger log = LoggerFactory.getLogger(NolioPushService.class);

    private final NolioApiClient apiClient;
    private final NolioWorkoutMapper mapper;
    private final TrainingRepository trainingRepository;
    private final UserService userService;

    public NolioPushService(NolioApiClient apiClient,
                            NolioWorkoutMapper mapper,
                            TrainingRepository trainingRepository,
                            UserService userService) {
        this.apiClient = apiClient;
        this.mapper = mapper;
        this.trainingRepository = trainingRepository;
        this.userService = userService;
    }

    /** Synchronous push — used by the manual "Push to Nolio" endpoint. */
    public Training push(String userId, String trainingId) {
        User user = userService.getUserById(userId);
        Training training = loadOwned(userId, trainingId);
        return pushInternal(user, training);
    }

    /** No-op unless the user has enabled auto-sync and connected Nolio write access. */
    public void autoSyncIfEnabled(String userId, Training training) {
        if (training == null || training.getId() == null) return;
        User user = userService.findById(userId).orElse(null);
        if (user == null) return;
        if (!Boolean.TRUE.equals(user.getNolioAutoSyncWorkouts())) return;
        if (user.getNolioAccessToken() == null) return;
        pushAsync(userId, training.getId());
    }

    /**
     * Async push — used by the auto-sync hook after training CRUD.
     * Failures are swallowed (recorded on the Training document) since the
     * user action that triggered this has already returned.
     */
    @Async
    public void pushAsync(String userId, String trainingId) {
        try {
            push(userId, trainingId);
        } catch (RuntimeException e) {
            log.warn("Async Nolio push failed for training {}: {}", trainingId, e.getMessage());
        }
    }

    /** Delete the remote workout, if one exists. Called when a Training is deleted locally. */
    public void deleteRemote(User user, String nolioWorkoutId) {
        if (user == null || nolioWorkoutId == null || user.getNolioAccessToken() == null) return;
        try {
            apiClient.deleteWorkout(user, nolioWorkoutId);
        } catch (RuntimeException e) {
            log.warn("Nolio delete failed for workout {}: {}", nolioWorkoutId, e.getMessage());
        }
    }

    private Training pushInternal(User user, Training training) {
        if (user.getNolioAccessToken() == null) {
            throw new IllegalStateException("User has not connected Nolio write access");
        }

        training.setNolioSyncStatus(NolioSyncStatus.PENDING);
        training.setNolioSyncError(null);
        trainingRepository.save(training);

        try {
            Map<String, Object> payload = mapper.toPayload(training);
            String workoutId;
            if (training.getNolioWorkoutId() != null) {
                apiClient.updateWorkout(user, training.getNolioWorkoutId(), payload);
                workoutId = training.getNolioWorkoutId();
            } else {
                workoutId = apiClient.createWorkout(user, payload);
            }
            training.setNolioWorkoutId(workoutId);
            training.setNolioSyncStatus(NolioSyncStatus.SYNCED);
            training.setNolioLastSyncedAt(LocalDateTime.now());
            training.setNolioSyncError(null);
        } catch (RuntimeException e) {
            training.setNolioSyncStatus(NolioSyncStatus.FAILED);
            training.setNolioSyncError(truncate(e.getMessage()));
            trainingRepository.save(training);
            throw e;
        }

        return trainingRepository.save(training);
    }

    private Training loadOwned(String userId, String trainingId) {
        Training training = trainingRepository.findById(trainingId)
                .orElseThrow(() -> new IllegalArgumentException("Training not found: " + trainingId));
        if (!userId.equals(training.getCreatedBy())) {
            throw new IllegalStateException("User " + userId + " does not own training " + trainingId);
        }
        return training;
    }

    private static String truncate(String message) {
        if (message == null) return null;
        return message.length() > 500 ? message.substring(0, 500) : message;
    }
}
