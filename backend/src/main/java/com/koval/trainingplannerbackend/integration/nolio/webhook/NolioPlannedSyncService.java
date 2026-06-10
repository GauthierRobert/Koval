package com.koval.trainingplannerbackend.integration.nolio.webhook;

import com.fasterxml.jackson.databind.JsonNode;
import com.koval.trainingplannerbackend.auth.User;
import com.koval.trainingplannerbackend.auth.UserRepository;
import com.koval.trainingplannerbackend.coach.ScheduledWorkout;
import com.koval.trainingplannerbackend.coach.ScheduledWorkoutRepository;
import com.koval.trainingplannerbackend.integration.nolio.write.NolioApiClient;
import com.koval.trainingplannerbackend.integration.nolio.write.NolioSyncStatus;
import com.koval.trainingplannerbackend.training.TrainingRepository;
import com.koval.trainingplannerbackend.training.model.Training;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Optional;

/**
 * Handles the planned-events webhook channel: Nolio-native planned trainings
 * are imported as a {@link Training} plus a {@link ScheduledWorkout} on the
 * planned date; edits and deletions in Nolio follow through.
 *
 * Echo handling — our own pushes come back on this channel too. They are
 * recognised by {@code nolioRemoteId} (captured from the create response) and
 * skipped; a freshly-synced training with the same title is adopted as a
 * fallback when the create response carried no id.
 */
@Service
public class NolioPlannedSyncService {

    private static final Logger log = LoggerFactory.getLogger(NolioPlannedSyncService.class);
    /** Window in which a new_planned_event with a matching title is assumed to echo our own push. */
    private static final Duration ECHO_ADOPTION_WINDOW = Duration.ofMinutes(15);

    private final UserRepository userRepository;
    private final NolioApiClient apiClient;
    private final NolioPlannedTrainingImportMapper mapper;
    private final TrainingRepository trainingRepository;
    private final ScheduledWorkoutRepository scheduledWorkoutRepository;

    public NolioPlannedSyncService(UserRepository userRepository,
                                   NolioApiClient apiClient,
                                   NolioPlannedTrainingImportMapper mapper,
                                   TrainingRepository trainingRepository,
                                   ScheduledWorkoutRepository scheduledWorkoutRepository) {
        this.userRepository = userRepository;
        this.apiClient = apiClient;
        this.mapper = mapper;
        this.trainingRepository = trainingRepository;
        this.scheduledWorkoutRepository = scheduledWorkoutRepository;
    }

    @Async
    public void handleAsync(NolioWebhookPayload payload) {
        try {
            handle(payload);
        } catch (RuntimeException e) {
            log.warn("Nolio planned-event webhook {} {} failed: {}",
                    payload.notifType(), payload.objectId(), e.getMessage());
        }
    }

    private void handle(NolioWebhookPayload payload) {
        if (!"TrainingPlanned".equals(payload.objectType())) {
            log.debug("Ignoring Nolio planned event for unsupported object_type {}", payload.objectType());
            return;
        }
        User user = userRepository.findByNolioUserId(String.valueOf(payload.userId())).orElse(null);
        if (user == null || user.getNolioAccessToken() == null) {
            log.info("Nolio planned event for unknown/unlinked nolio user {} — skipped", payload.userId());
            return;
        }

        String remoteId = String.valueOf(payload.objectId());
        Optional<Training> known = trainingRepository.findFirstByCreatedByAndNolioRemoteId(user.getId(), remoteId);

        switch (payload.notifType()) {
            case "deleted_planned_event" -> known.ifPresent(training -> onRemoteDeleted(user, training));
            case "new_planned_event", "updated_planned_event" -> {
                if (known.isPresent() && isPushedByUs(known.get())) {
                    log.debug("Skipping echo of our own Nolio push (training {})", known.get().getId());
                    return;
                }
                upsertImported(user, payload.objectId(), remoteId, known.orElse(null));
            }
            default -> log.debug("Ignoring Nolio planned event notif_type {}", payload.notifType());
        }
    }

    private static boolean isPushedByUs(Training training) {
        return training.getNolioWorkoutId() != null;
    }

    private void upsertImported(User user, long nolioId, String remoteId, Training existing) {
        JsonNode planned = apiClient.getPlannedTraining(user, nolioId);
        if (planned == null) {
            log.warn("Nolio planned training {} not found when handling webhook for user {}", nolioId, user.getId());
            return;
        }
        if (existing == null && adoptEchoOfOwnPush(user, planned, remoteId)) {
            return;
        }

        Training mapped = mapper.map(planned, user.getFtp());
        if (mapped == null) {
            log.info("Nolio planned training {} has unsupported sport_id {} — skipped",
                    nolioId, planned.path("sport_id").asInt());
            return;
        }

        LocalDate plannedDate = parseDate(planned.path("date_start").asText(null));
        if (existing != null) {
            applyUpdate(existing, mapped);
            trainingRepository.save(existing);
            moveSchedule(user, existing, plannedDate);
            log.info("Updated imported training {} from Nolio planned training {}", existing.getId(), nolioId);
        } else {
            mapped.setCreatedBy(user.getId());
            mapped.setCreatedAt(LocalDateTime.now());
            mapped.setNolioRemoteId(remoteId);
            Training saved = trainingRepository.save(mapped);
            schedule(user, saved, plannedDate);
            log.info("Imported Nolio planned training {} as training {} for user {}", nolioId, saved.getId(), user.getId());
        }
    }

    /**
     * Fallback echo detection for pushes whose create response carried no Nolio id:
     * a training we synced moments ago with the same title is the same object —
     * adopt the remote id instead of importing a duplicate.
     */
    private boolean adoptEchoOfOwnPush(User user, JsonNode planned, String remoteId) {
        String name = planned.path("name").asText("");
        LocalDateTime cutoff = LocalDateTime.now().minus(ECHO_ADOPTION_WINDOW);
        return trainingRepository.findSummariesByCreatedBy(user.getId()).stream()
                .filter(t -> t.getNolioWorkoutId() != null && t.getNolioRemoteId() == null)
                .filter(t -> t.getNolioLastSyncedAt() != null && t.getNolioLastSyncedAt().isAfter(cutoff))
                .filter(t -> name.equals(t.getTitle()))
                .findFirst()
                .flatMap(summary -> trainingRepository.findById(summary.getId())) // summaries carry no blocks — reload before saving
                .map(t -> {
                    t.setNolioRemoteId(remoteId);
                    trainingRepository.save(t);
                    log.info("Adopted Nolio id {} for our own pushed training {}", remoteId, t.getId());
                    return true;
                })
                .orElse(false);
    }

    private void applyUpdate(Training target, Training fromNolio) {
        target.setTitle(fromNolio.getTitle());
        target.setDescription(fromNolio.getDescription());
        target.setEstimatedDurationSeconds(fromNolio.getEstimatedDurationSeconds());
        target.setEstimatedDistance(fromNolio.getEstimatedDistance());
        target.setEstimatedTss(fromNolio.getEstimatedTss());
        if (fromNolio.getBlocks() != null && !fromNolio.getBlocks().isEmpty()) {
            target.setBlocks(fromNolio.getBlocks());
        }
    }

    private void schedule(User user, Training training, LocalDate date) {
        if (date == null) return;
        ScheduledWorkout workout = new ScheduledWorkout();
        workout.setTrainingId(training.getId());
        workout.setAthleteId(user.getId());
        workout.setAssignedBy(user.getId());
        workout.setScheduledDate(date);
        workout.setTss(training.getEstimatedTss());
        scheduledWorkoutRepository.save(workout);
    }

    private void moveSchedule(User user, Training training, LocalDate date) {
        if (date == null) return;
        importedSchedules(user, training).forEach(workout -> {
            if (!date.equals(workout.getScheduledDate())) {
                workout.setScheduledDate(date);
                scheduledWorkoutRepository.save(workout);
            }
        });
    }

    private void onRemoteDeleted(User user, Training training) {
        if (isPushedByUs(training)) {
            // The user removed our pushed workout in Nolio: drop the link so a
            // future push recreates it, but keep the Koval training itself.
            training.setNolioWorkoutId(null);
            training.setNolioRemoteId(null);
            training.setNolioSyncStatus(NolioSyncStatus.NONE);
            training.setNolioSyncError(null);
            trainingRepository.save(training);
            log.info("Nolio-side delete of pushed training {} — sync link cleared", training.getId());
        } else {
            List<ScheduledWorkout> schedules = importedSchedules(user, training);
            scheduledWorkoutRepository.deleteAll(schedules);
            trainingRepository.delete(training);
            log.info("Deleted imported training {} ({} schedule entries) after Nolio-side removal",
                    training.getId(), schedules.size());
        }
    }

    private List<ScheduledWorkout> importedSchedules(User user, Training training) {
        return scheduledWorkoutRepository.findByTrainingId(training.getId()).stream()
                .filter(w -> user.getId().equals(w.getAthleteId()))
                .toList();
    }

    private static LocalDate parseDate(String date) {
        if (date == null || date.isBlank()) return null;
        try {
            return LocalDate.parse(date);
        } catch (DateTimeParseException e) {
            return null;
        }
    }
}
