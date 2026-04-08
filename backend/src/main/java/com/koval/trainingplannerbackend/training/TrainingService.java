package com.koval.trainingplannerbackend.training;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.koval.trainingplannerbackend.club.membership.ClubMemberStatus;
import com.koval.trainingplannerbackend.club.membership.ClubMembership;
import com.koval.trainingplannerbackend.club.membership.ClubMembershipRepository;
import com.koval.trainingplannerbackend.config.exceptions.ResourceNotFoundException;
import com.koval.trainingplannerbackend.integration.zwift.ZwiftWorkoutService;
import com.koval.trainingplannerbackend.training.metrics.TrainingMetricsService;
import com.koval.trainingplannerbackend.training.model.BlockType;
import com.koval.trainingplannerbackend.training.model.Training;
import com.koval.trainingplannerbackend.training.model.WorkoutElement;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static java.util.Optional.ofNullable;

/**
 * Service for Training CRUD operations.
 */
@Service
public class TrainingService {

    private final TrainingRepository trainingRepository;
    private final TrainingMetricsService metricsService;
    private final ClubMembershipRepository membershipRepository;
    private final ZwiftWorkoutService zwiftWorkoutService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public TrainingService(TrainingRepository trainingRepository,
                           TrainingMetricsService metricsService,
                           ClubMembershipRepository membershipRepository,
                           ZwiftWorkoutService zwiftWorkoutService) {
        this.trainingRepository = trainingRepository;
        this.metricsService = metricsService;
        this.membershipRepository = membershipRepository;
        this.zwiftWorkoutService = zwiftWorkoutService;
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
        } catch (Exception e) {
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
        training.setBlocks(training.getBlocks().stream().map(this::standardizeBlockType).toList());
        Training saved = trainingRepository.save(training);
        zwiftWorkoutService.autoSyncIfEnabled(userId, saved);
        return saved;
    }

    /**
     * Because AI can be Wrong — recurses into sets.
     */
    private WorkoutElement standardizeBlockType(WorkoutElement element) {
        if (element.isSet()) {
            var standardizedChildren = element.elements().stream()
                    .map(this::standardizeBlockType)
                    .toList();
            return element.withElements(standardizedChildren);
        }

        if (isPositive(element.intensityEnd()) && isPositive(element.intensityStart())) {
            return element.updateType(BlockType.RAMP);
        }

        // Don't reclassify zone-targeted blocks as PAUSE — they may have no intensity before enrichment
        if (element.zoneTarget() != null && !element.zoneTarget().isBlank()) {
            return element;
        }

        // Preserve TRANSITION blocks — they intentionally have no intensity
        if (element.type() == BlockType.TRANSITION) {
            return element;
        }

        if (!isPositive(element.intensityEnd()) && !isPositive(element.intensityStart())
                && !isPositive(element.intensityTarget())) {
            return element.updateType(BlockType.PAUSE);
        }
        return element;
    }

    private static boolean isPositive(Integer val) {
        return val != null && val > 0;
    }

    /**
     * Update an existing training.
     */
    public Training updateTraining(String trainingId, Training updates) {
        Training training = trainingRepository.findById(trainingId)
                .orElseThrow(() -> new ResourceNotFoundException("Training", trainingId));

        applyPartialUpdates(training, updates);

        metricsService.calculateTrainingMetrics(training, training.getCreatedBy());
        return trainingRepository.save(training);
    }

    private void applyPartialUpdates(Training training, Training updates) {
        ofNullable(updates.getTitle()).ifPresent(training::setTitle);
        ofNullable(updates.getDescription()).ifPresent(training::setDescription);
        ofNullable(updates.getSportType()).ifPresent(training::setSportType);
        ofNullable(updates.getBlocks()).ifPresent(blocks ->
                training.setBlocks(blocks.stream().map(this::standardizeBlockType).toList()));
        ofNullable(updates.getGroupIds()).ifPresent(training::setGroupIds);
        ofNullable(updates.getTrainingType()).ifPresent(training::setTrainingType);
        ofNullable(updates.getClubIds()).ifPresent(training::setClubIds);
        ofNullable(updates.getClubGroupIds()).ifPresent(training::setClubGroupIds);
        ofNullable(updates.getZoneSystemId()).ifPresent(training::setZoneSystemId);
    }

    /**
     * Delete a training.
     */
    public void deleteTraining(String trainingId) {
        if (!trainingRepository.existsById(trainingId)) {
            throw new ResourceNotFoundException("Training", trainingId);
        }
        trainingRepository.deleteById(trainingId);
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
     * Discover trainings from clubs the user is an active member of.
     */
    public List<Training> discoverClubTrainings(String userId) {
        List<ClubMembership> memberships = membershipRepository.findByUserId(userId);
        List<String> clubIds = memberships.stream()
                .filter(m -> m.getStatus() == ClubMemberStatus.ACTIVE)
                .map(ClubMembership::getClubId)
                .toList();
        if (clubIds.isEmpty()) return List.of();
        return trainingRepository.findByClubIdsIn(clubIds);
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
