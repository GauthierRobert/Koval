package com.koval.trainingplannerbackend.integration.nolio.write;

import com.fasterxml.jackson.databind.JsonNode;
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
    private final NolioPartnerIdGenerator partnerIdGenerator;
    private final TrainingRepository trainingRepository;
    private final UserService userService;

    public NolioPushService(NolioApiClient apiClient,
                            NolioWorkoutMapper mapper,
                            NolioPartnerIdGenerator partnerIdGenerator,
                            TrainingRepository trainingRepository,
                            UserService userService) {
        this.apiClient = apiClient;
        this.mapper = mapper;
        this.partnerIdGenerator = partnerIdGenerator;
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
        // Trainings imported FROM Nolio webhooks must never be pushed back — that
        // would duplicate the Nolio-native object (we can't address it by id_partner).
        if (training.getNolioRemoteId() != null && training.getNolioWorkoutId() == null) return;
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
        if (user == null || user.getNolioAccessToken() == null) return;
        Long idPartner = parsePartnerId(nolioWorkoutId);
        if (idPartner == null) return;
        try {
            apiClient.deletePlannedTraining(user, idPartner);
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
            Long idPartner = parsePartnerId(training.getNolioWorkoutId());
            boolean isFirstPush = idPartner == null;
            if (isFirstPush) {
                idPartner = partnerIdGenerator.next();
            }
            Map<String, Object> payload = mapper.toPayload(training, idPartner, user.getFtp());
            if (isFirstPush) {
                JsonNode created = apiClient.createPlannedTraining(user, payload);
                captureRemoteId(training, created);
            } else {
                apiClient.updatePlannedTraining(user, payload);
            }
            training.setNolioWorkoutId(String.valueOf(idPartner));
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

    /**
     * Remembers Nolio's own id for the object we just created, so planned-event
     * webhooks echoing our push can be recognised and skipped.
     */
    private static void captureRemoteId(Training training, JsonNode created) {
        if (created == null) return;
        JsonNode node = created.isArray() && !created.isEmpty() ? created.get(0) : created;
        for (String field : new String[]{"nolio_id", "id", "pk"}) {
            if (node.hasNonNull(field)) {
                training.setNolioRemoteId(node.get(field).asText());
                return;
            }
        }
        log.info("Nolio create response carried no recognizable id — echo suppression will rely on id_partner only");
    }

    /** The stored id is our numeric id_partner; anything non-numeric means "never pushed". */
    private static Long parsePartnerId(String nolioWorkoutId) {
        if (nolioWorkoutId == null) return null;
        try {
            return Long.parseLong(nolioWorkoutId);
        } catch (NumberFormatException e) {
            return null;
        }
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
