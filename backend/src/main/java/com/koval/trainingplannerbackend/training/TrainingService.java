package com.koval.trainingplannerbackend.training;

import com.koval.trainingplannerbackend.club.ClubMemberStatus;
import com.koval.trainingplannerbackend.club.ClubMembership;
import com.koval.trainingplannerbackend.club.ClubMembershipRepository;
import com.koval.trainingplannerbackend.config.exceptions.ResourceNotFoundException;
import com.koval.trainingplannerbackend.training.metrics.TrainingMetricsService;
import com.koval.trainingplannerbackend.training.model.BlockType;
import com.koval.trainingplannerbackend.training.model.Training;
import com.koval.trainingplannerbackend.training.model.WorkoutElement;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static java.util.Optional.ofNullable;

/**
 * Service for Training CRUD operations.
 */
@Service
public class TrainingService {

    private final TrainingRepository trainingRepository;
    private final TrainingMetricsService metricsService;
    private final ClubMembershipRepository membershipRepository;

    public TrainingService(TrainingRepository trainingRepository,
                           TrainingMetricsService metricsService,
                           ClubMembershipRepository membershipRepository) {
        this.trainingRepository = trainingRepository;
        this.metricsService = metricsService;
        this.membershipRepository = membershipRepository;
    }

    /**
     * Create a new training workout.
     */
    public Training createTraining(Training training, String userId) {
        training.setCreatedBy(userId);
        training.setCreatedAt(LocalDateTime.now());
        metricsService.calculateTrainingMetrics(training, userId);
        training.setBlocks(training.getBlocks().stream().map(this::standardizeBlockType).toList());
        return trainingRepository.save(training);
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

        if ((element.intensityEnd() != null && element.intensityEnd() > 0) &&
            (element.intensityStart() != null && element.intensityStart() > 0)) {
            return element.updateType(BlockType.RAMP);
        }

        // Don't reclassify zone-targeted blocks as PAUSE — they may have no intensity before enrichment
        if (element.zoneTarget() != null && !element.zoneTarget().isBlank()) {
            return element;
        }

        if ((element.intensityEnd() == null || element.intensityEnd() == 0) &&
            (element.intensityStart() == null || element.intensityStart() == 0) &&
            (element.intensityTarget() == null || element.intensityTarget() == 0)) {
            return element.updateType(BlockType.PAUSE);
        }
        return element;
    }

    /**
     * Update an existing training.
     */
    public Training updateTraining(String trainingId, Training updates) {
        Optional<Training> existing = trainingRepository.findById(trainingId);
        if (existing.isEmpty()) {
            throw new ResourceNotFoundException("Training", trainingId);
        }

        Training training = existing.get();
        ofNullable(updates.getTitle()).ifPresent(training::setTitle);
        ofNullable(updates.getDescription()).ifPresent(training::setDescription);
        ofNullable(updates.getBlocks()).ifPresent(blocks ->
                training.setBlocks(blocks.stream().map(this::standardizeBlockType).toList()));
        ofNullable(updates.getGroupIds()).ifPresent(training::setGroupIds);
        ofNullable(updates.getTrainingType()).ifPresent(training::setTrainingType);
        ofNullable(updates.getClubIds()).ifPresent(training::setClubIds);
        ofNullable(updates.getClubGroupIds()).ifPresent(training::setClubGroupIds);
        ofNullable(updates.getZoneSystemId()).ifPresent(training::setZoneSystemId);

        metricsService.calculateTrainingMetrics(training, existing.get().getCreatedBy());
        return trainingRepository.save(training);
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
