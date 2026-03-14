package com.koval.trainingplannerbackend.training;

import com.koval.trainingplannerbackend.club.ClubMembership;
import com.koval.trainingplannerbackend.club.ClubMemberStatus;
import com.koval.trainingplannerbackend.club.ClubMembershipRepository;
import com.koval.trainingplannerbackend.training.group.Group;
import com.koval.trainingplannerbackend.training.group.GroupService;
import com.koval.trainingplannerbackend.training.metrics.TrainingMetricsService;
import com.koval.trainingplannerbackend.training.model.BlockType;
import com.koval.trainingplannerbackend.training.model.Training;
import com.koval.trainingplannerbackend.training.model.TrainingType;
import com.koval.trainingplannerbackend.training.model.WorkoutBlock;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Service for Training CRUD operations.
 */
@Service
public class TrainingService {

    private final TrainingRepository trainingRepository;
    private final GroupService groupService;
    private final TrainingMetricsService metricsService;
    private final ClubMembershipRepository membershipRepository;

    public TrainingService(TrainingRepository trainingRepository,
                           GroupService groupService,
                           TrainingMetricsService metricsService,
                           ClubMembershipRepository membershipRepository) {
        this.trainingRepository = trainingRepository;
        this.groupService = groupService;
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
     * Because AI can be Wrong
     */
    private WorkoutBlock standardizeBlockType(WorkoutBlock workoutBlock) {
        if ((workoutBlock.intensityEnd() != null && workoutBlock.intensityEnd() > 0) &&
            (workoutBlock.intensityStart() != null && workoutBlock.intensityStart() > 0)) {
            return workoutBlock.updateType(BlockType.RAMP);
        }

        // Don't reclassify zone-targeted blocks as PAUSE — they may have no intensity before enrichment
        if (workoutBlock.zoneTarget() != null && !workoutBlock.zoneTarget().isBlank()) {
            return workoutBlock;
        }

        if ((workoutBlock.intensityEnd() == null || workoutBlock.intensityEnd() == 0) &&
            (workoutBlock.intensityStart() == null || workoutBlock.intensityStart() == 0) &&
            (workoutBlock.intensityTarget() == null || workoutBlock.intensityTarget() == 0)) {
            return workoutBlock.updateType(BlockType.PAUSE);
        }
        return workoutBlock;
    }

    /**
     * Update an existing training.
     */
    public Training updateTraining(String trainingId, Training updates) {
        Optional<Training> existing = trainingRepository.findById(trainingId);
        if (existing.isEmpty()) {
            throw new IllegalArgumentException("Training not found: " + trainingId);
        }

        Training training = existing.get();
        if (updates.getTitle() != null)
            training.setTitle(updates.getTitle());
        if (updates.getDescription() != null)
            training.setDescription(updates.getDescription());
        if (updates.getBlocks() != null)
            training.setBlocks((updates.getBlocks().stream().map(this::standardizeBlockType).toList()));
        if (updates.getGroupIds() != null)
            training.setGroupIds(updates.getGroupIds());
        if (updates.getTrainingType() != null)
            training.setTrainingType(updates.getTrainingType());
        if (updates.getClubId() != null)
            training.setClubId(updates.getClubId());
        if (updates.getClubGroupIds() != null)
            training.setClubGroupIds(updates.getClubGroupIds());
        if (updates.getZoneSystemId() != null)
            training.setZoneSystemId(updates.getZoneSystemId());

        metricsService.calculateTrainingMetrics(training, existing.get().getCreatedBy());
        return trainingRepository.save(training);
    }

    /**
     * Delete a training.
     */
    public void deleteTraining(String trainingId) {
        if (!trainingRepository.existsById(trainingId)) {
            throw new IllegalArgumentException("Training not found: " + trainingId);
        }
        trainingRepository.deleteById(trainingId);
    }

    /**
     * Get a training by ID.
     */
    public Training getTrainingById(String trainingId) {
        return trainingRepository.findById(trainingId)
                .orElseThrow(() -> new IllegalArgumentException("Training not found: " + trainingId));
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
     * Search trainings by group (group ID).
     */
    public List<Training> searchByGroup(String groupId) {
        return trainingRepository.findByGroupIdsContaining(groupId);
    }

    /**
     * Search trainings by training type.
     */
    public List<Training> searchByType(TrainingType trainingType) {
        return trainingRepository.findByTrainingType(trainingType);
    }

    /**
     * Discover trainings available to an athlete based on their groups.
     */
    public List<Training> discoverTrainingsByUserGroups(String athleteId) {
        List<Group> athleteGroups = groupService.getGroupsForAthlete(athleteId);
        if (athleteGroups.isEmpty()) {
            return List.of();
        }

        List<String> groupIds = athleteGroups.stream().map(Group::getId).toList();
        return trainingRepository.findByGroupIdsIn(groupIds);
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
        return trainingRepository.findByClubIdIn(clubIds);
    }

    /**
     * Check if a user is an active member of a club.
     */
    public boolean isUserActiveClubMember(String userId, String clubId) {
        if (clubId == null) return false;
        return membershipRepository.findByClubIdAndUserId(clubId, userId)
                .map(m -> m.getStatus() == ClubMemberStatus.ACTIVE)
                .orElse(false);
    }
}
