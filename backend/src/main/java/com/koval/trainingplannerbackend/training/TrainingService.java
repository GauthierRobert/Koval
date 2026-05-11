package com.koval.trainingplannerbackend.training;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.koval.trainingplannerbackend.club.membership.ClubMemberStatus;
import com.koval.trainingplannerbackend.club.membership.ClubMembership;
import com.koval.trainingplannerbackend.club.membership.ClubMembershipRepository;
import com.koval.trainingplannerbackend.auth.UserService;
import com.koval.trainingplannerbackend.config.exceptions.ResourceNotFoundException;
import com.koval.trainingplannerbackend.integration.nolio.write.NolioPushService;
import com.koval.trainingplannerbackend.integration.zwift.ZwiftWorkoutService;
import com.koval.trainingplannerbackend.training.metrics.TrainingMetricsService;
import com.koval.trainingplannerbackend.training.model.Training;
import com.koval.trainingplannerbackend.training.zone.ZoneSystem;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static java.util.Optional.ofNullable;

/**
 * Service for Training CRUD operations.
 *
 * <p>Block-type normalization and zone resolution are delegated to
 * {@link WorkoutBlockStandardizer}.
 */
@Service
public class TrainingService {

    private final TrainingRepository trainingRepository;
    private final TrainingMetricsService metricsService;
    private final ClubMembershipRepository membershipRepository;
    private final ZwiftWorkoutService zwiftWorkoutService;
    private final NolioPushService nolioPushService;
    private final UserService userService;
    private final ObjectMapper objectMapper;
    private final WorkoutBlockStandardizer blockStandardizer;

    public TrainingService(TrainingRepository trainingRepository,
                           TrainingMetricsService metricsService,
                           ClubMembershipRepository membershipRepository,
                           ZwiftWorkoutService zwiftWorkoutService,
                           NolioPushService nolioPushService,
                           UserService userService,
                           ObjectMapper objectMapper,
                           WorkoutBlockStandardizer blockStandardizer) {
        this.trainingRepository = trainingRepository;
        this.metricsService = metricsService;
        this.membershipRepository = membershipRepository;
        this.zwiftWorkoutService = zwiftWorkoutService;
        this.nolioPushService = nolioPushService;
        this.userService = userService;
        this.objectMapper = objectMapper;
        this.blockStandardizer = blockStandardizer;
    }

    /**
     * Duplicate an existing training. The copy is owned by {@code userId}, has a fresh
     * id, a "(copy)" suffix on the title, and is detached from any club assignments.
     */
    public Training duplicateTraining(String trainingId, String userId) {
        Training source = getTrainingById(trainingId);
        Training copy;
        try {
            // JSON round-trip handles polymorphism (CyclingTraining, RunningTraining, etc.)
            String json = objectMapper.writeValueAsString(source);
            copy = objectMapper.readValue(json, Training.class);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to clone training: " + e.getMessage(), e);
        }
        copy.setId(null);
        copy.setCreatedBy(userId);
        copy.setCreatedAt(LocalDateTime.now());
        copy.setTitle(source.getTitle() + " (copy)");
        copy.setClubIds(new ArrayList<>());
        copy.setClubGroupIds(new ArrayList<>());
        copy.setGroupIds(new ArrayList<>());
        return trainingRepository.save(copy);
    }

    /**
     * Create a new training workout.
     */
    public Training createTraining(Training training, String userId) {
        training.setCreatedBy(userId);
        training.setCreatedAt(LocalDateTime.now());
        metricsService.calculateTrainingMetrics(training, userId);
        ZoneSystem zoneSystem = blockStandardizer.resolveZoneSystem(training, userId);
        training.setBlocks(blockStandardizer.standardize(training.getBlocks(), zoneSystem));
        Training saved = trainingRepository.save(training);
        zwiftWorkoutService.autoSyncIfEnabled(userId, saved);
        nolioPushService.autoSyncIfEnabled(userId, saved);
        return saved;
    }

    /**
     * Update an existing training.
     */
    public Training updateTraining(String trainingId, Training updates) {
        Training training = trainingRepository.findById(trainingId)
                .orElseThrow(() -> new ResourceNotFoundException("Training", trainingId));

        applyPartialUpdates(training, updates);

        metricsService.calculateTrainingMetrics(training, training.getCreatedBy());
        Training saved = trainingRepository.save(training);
        nolioPushService.autoSyncIfEnabled(saved.getCreatedBy(), saved);
        return saved;
    }

    private void applyPartialUpdates(Training training, Training updates) {
        ofNullable(updates.getTitle()).ifPresent(training::setTitle);
        ofNullable(updates.getDescription()).ifPresent(training::setDescription);
        ofNullable(updates.getSportType()).ifPresent(training::setSportType);
        // Apply zoneSystemId before block standardization so the latter can resolve zones.
        ofNullable(updates.getZoneSystemId()).ifPresent(training::setZoneSystemId);
        ofNullable(updates.getBlocks()).ifPresent(blocks -> {
            ZoneSystem zoneSystem = blockStandardizer.resolveZoneSystem(training, training.getCreatedBy());
            training.setBlocks(blockStandardizer.standardize(blocks, zoneSystem));
        });
        ofNullable(updates.getGroupIds()).ifPresent(training::setGroupIds);
        ofNullable(updates.getTrainingType()).ifPresent(training::setTrainingType);
        ofNullable(updates.getClubIds()).ifPresent(training::setClubIds);
        ofNullable(updates.getClubGroupIds()).ifPresent(training::setClubGroupIds);
    }

    /**
     * Delete a training.
     */
    public void deleteTraining(String trainingId) {
        Training training = trainingRepository.findById(trainingId)
                .orElseThrow(() -> new ResourceNotFoundException("Training", trainingId));

        String nolioWorkoutId = training.getNolioWorkoutId();
        String ownerId = training.getCreatedBy();

        trainingRepository.deleteById(trainingId);

        if (nolioWorkoutId != null && ownerId != null) {
            userService.findById(ownerId).ifPresent(owner ->
                    nolioPushService.deleteRemote(owner, nolioWorkoutId));
        }
    }

    /**
     * Get a training by ID.
     */
    public Training getTrainingById(String trainingId) {
        return trainingRepository.findById(trainingId)
                .orElseThrow(() -> new ResourceNotFoundException("Training", trainingId));
    }

    /**
     * List trainings created by a user.
     */
    public List<Training> listTrainingsByUser(String userId) {
        return trainingRepository.findByCreatedBy(userId);
    }

    /**
     * List trainings created by a user with pagination.
     */
    public Page<Training> listTrainingsByUser(String userId, Pageable pageable) {
        return trainingRepository.findByCreatedBy(userId, pageable);
    }

    /**
     * Lightweight summaries for list endpoints — excludes {@code blocks} at the
     * Mongo driver level so payloads are a few hundred bytes per item instead of
     * 50-100KB.
     */
    public List<TrainingSummary> listSummariesByUser(String userId) {
        return trainingRepository.findSummariesByCreatedBy(userId).stream()
                .map(TrainingSummary::from)
                .toList();
    }

    public Page<TrainingSummary> listSummariesByUser(String userId, Pageable pageable) {
        return trainingRepository.findSummariesByCreatedBy(userId, pageable)
                .map(TrainingSummary::from);
    }

    public List<TrainingSummary> discoverClubTrainingSummaries(String userId) {
        List<String> clubIds = activeClubIds(userId);
        if (clubIds.isEmpty()) return List.of();
        return trainingRepository.findSummariesByClubIdsIn(clubIds).stream()
                .map(TrainingSummary::from)
                .toList();
    }

    /**
     * Discover trainings from clubs the user is an active member of.
     */
    public List<Training> discoverClubTrainings(String userId) {
        List<String> clubIds = activeClubIds(userId);
        if (clubIds.isEmpty()) return List.of();
        return trainingRepository.findByClubIdsIn(clubIds);
    }

    private List<String> activeClubIds(String userId) {
        List<ClubMembership> memberships = membershipRepository.findByUserId(userId);
        return memberships.stream()
                .filter(m -> m.getStatus() == ClubMemberStatus.ACTIVE)
                .map(ClubMembership::getClubId)
                .toList();
    }

    /**
     * Add a club ID to a training's clubIds list (idempotent).
     */
    public Training addClubIdToTraining(String trainingId, String clubId) {
        Training training = getTrainingById(trainingId);
        training.addClubId(clubId);
        return trainingRepository.save(training);
    }

}
