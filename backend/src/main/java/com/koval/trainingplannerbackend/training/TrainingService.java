package com.koval.trainingplannerbackend.training;

import com.koval.trainingplannerbackend.auth.User;
import com.koval.trainingplannerbackend.auth.UserRepository;
import com.koval.trainingplannerbackend.club.ClubMembership;
import com.koval.trainingplannerbackend.club.ClubMemberStatus;
import com.koval.trainingplannerbackend.club.ClubMembershipRepository;
import com.koval.trainingplannerbackend.training.group.Group;
import com.koval.trainingplannerbackend.training.group.GroupService;
import com.koval.trainingplannerbackend.training.metrics.TssCalculator;
import com.koval.trainingplannerbackend.training.model.BlockType;
import com.koval.trainingplannerbackend.training.model.SportType;
import com.koval.trainingplannerbackend.training.model.Training;
import com.koval.trainingplannerbackend.training.model.TrainingType;
import com.koval.trainingplannerbackend.training.model.WorkoutBlock;
import com.koval.trainingplannerbackend.training.zone.ZoneSystem;
import com.koval.trainingplannerbackend.training.zone.ZoneSystemService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Service for Training CRUD operations.
 * These methods are designed to be exposed to the AI model via function
 * calling.
 */
@Service
public class TrainingService {

    private final TrainingRepository trainingRepository;
    private final GroupService groupService;
    private final UserRepository userRepository;
    private final ZoneSystemService zoneSystemService;
    private final ClubMembershipRepository membershipRepository;

    public TrainingService(TrainingRepository trainingRepository,
                           GroupService groupService,
                           UserRepository userRepository,
                           ZoneSystemService zoneSystemService,
                           ClubMembershipRepository membershipRepository) {
        this.trainingRepository = trainingRepository;
        this.groupService = groupService;
        this.userRepository = userRepository;
        this.zoneSystemService = zoneSystemService;
        this.membershipRepository = membershipRepository;
    }

    /**
     * Create a new training workout.
     */
    public Training createTraining(Training training, String userId) {
        training.setCreatedBy(userId);
        training.setCreatedAt(LocalDateTime.now());
        calculateTrainingMetrics(training, userId);
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

        calculateTrainingMetrics(training, existing.get().getCreatedBy());
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
     * Uses GroupService to find athlete's groups, then finds trainings with those group
     * IDs.
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
     * Get training folders for an athlete grouped by group name.
     * Batch-loads all trainings in a single query to avoid N+1 queries per group.
     */
    public Map<String, List<Training>> getTrainingFolders(String athleteId) {
        List<Group> athleteGroups = groupService.getGroupsForAthlete(athleteId);
        if (athleteGroups.isEmpty()) {
            return Map.of();
        }

        List<String> groupIds = athleteGroups.stream().map(Group::getId).toList();
        List<Training> allTrainings = trainingRepository.findByGroupIdsIn(groupIds);

        Map<String, String> groupIdToName = athleteGroups.stream()
                .collect(Collectors.toMap(Group::getId, Group::getName, (a, b) -> a));

        Map<String, List<Training>> folders = new HashMap<>();
        for (Training t : allTrainings) {
            if (t.getGroupIds() == null) continue;
            for (String gid : t.getGroupIds()) {
                String name = groupIdToName.get(gid);
                if (name != null) {
                    folders.computeIfAbsent(name, k -> new ArrayList<>()).add(t);
                }
            }
        }

        return folders;
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

    /**
     * Re-calculates estimation metrics (duration, distance, TSS, IF) using the
     * requesting user's reference values instead of the creator's.
     */
    public void enrichTrainingForUser(Training training, String userId) {
        if (training.getBlocks() == null || training.getBlocks().isEmpty()) {
            return;
        }
        resolveZoneTargets(training, userId);
        calculateTrainingMetrics(training, userId);
    }

    /**
     * Resolves zone-targeted blocks to concrete intensity values using the zone system.
     */
    private void resolveZoneTargets(Training training, String userId) {
        boolean hasZoneTargets = training.getBlocks().stream()
                .anyMatch(b -> b.zoneTarget() != null && !b.zoneTarget().isBlank());
        if (!hasZoneTargets) return;

        ZoneSystem zoneSystem = resolveZoneSystem(training, userId);
        if (zoneSystem == null || zoneSystem.getZones() == null || zoneSystem.getZones().isEmpty()) return;

        Map<String, ZoneResolution> zoneMap = zoneSystem.getZones().stream()
                .filter(z -> z.label() != null)
                .collect(Collectors.toMap(
                        z -> z.label().toUpperCase(),
                        z -> new ZoneResolution((z.low() + z.high()) / 2,
                                z.label() + " - " + (z.description() != null ? z.description() : "") + " (" + z.low() + "-" + z.high() + "%)"),
                        (a, b) -> a));

        List<WorkoutBlock> resolvedBlocks = training.getBlocks().stream()
                .map(block -> {
                    if (block.zoneTarget() != null && !block.zoneTarget().isBlank()
                            && (block.intensityTarget() == null || block.intensityTarget() == 0)) {
                        ZoneResolution resolution = zoneMap.get(block.zoneTarget().toUpperCase());
                        if (resolution != null) {
                            return block.withResolvedIntensity(resolution.midpoint(), resolution.displayLabel());
                        }
                    }
                    return block;
                })
                .toList();

        training.setBlocks(resolvedBlocks);
    }

    private record ZoneResolution(int midpoint, String displayLabel) {}

    private ZoneSystem resolveZoneSystem(Training training, String userId) {
        // 1. Try training's explicit zone system
        if (training.getZoneSystemId() != null && !training.getZoneSystemId().isBlank()) {
            try {
                return zoneSystemService.getZoneSystem(training.getZoneSystemId());
            } catch (Exception ignored) {}
        }

        SportType sport = training.getSportType() != null ? training.getSportType() : SportType.CYCLING;

        // 2. Try default zone system for the training creator
        String createdBy = training.getCreatedBy() != null ? training.getCreatedBy() : userId;
        Optional<ZoneSystem> defaultZs = zoneSystemService.getDefaultZoneSystem(createdBy, sport);
        if (defaultZs.isPresent()) return defaultZs.get();

        // 3. Try coach's default via group membership
        List<String> coachIds = groupService.getCoachIdsForAthlete(userId);
        for (String coachId : coachIds) {
            Optional<ZoneSystem> coachZs = zoneSystemService.getDefaultZoneSystem(coachId, sport);
            if (coachZs.isPresent()) return coachZs.get();
        }

        return null;
    }

    /**
     * Calculates estimated TSS and IF for the training based on user's thresholds.
     */
    private void calculateTrainingMetrics(Training training, String userId) {
        if (training.getBlocks() == null || training.getBlocks().isEmpty()) {
            training.setEstimatedTss(0);
            training.setEstimatedIf(0.0);
            return;
        }

        Optional<User> userOpt = userRepository.findById(userId);
        if (userOpt.isEmpty()) {
            return;
        }
        User user = userOpt.get();
        SportType sport = training.getSportType();
        if (sport == null)
            sport = SportType.CYCLING;

        MetricsResult result = calculateBlocksMetrics(training, user, sport);

        training.setEstimatedTss((int) Math.round(result.totalTss()));

        if (result.totalDurationSeconds() > 0) {
            double estimatedIf = TssCalculator.computeIf(result.totalTss(), result.totalDurationSeconds());
            training.setEstimatedIf(Math.round(estimatedIf * 100.0) / 100.0);
            training.setEstimatedDurationSeconds(result.totalDurationSeconds());
            training.setEstimatedDistance(result.totalDistance());
        } else {
            training.setEstimatedIf(0.0);
        }
    }

    private record MetricsResult(double totalTss, int totalDurationSeconds, int totalDistance) {
    }

    private MetricsResult calculateBlocksMetrics(Training training, User user, SportType sport) {
        int ftpPaceSecPerKm = user.getFunctionalThresholdPace() != null ? user.getFunctionalThresholdPace() : 300;
        int cssSecPer100m = user.getCriticalSwimSpeed() != null ? user.getCriticalSwimSpeed() : 120;

        int totalDurationSeconds = 0;
        int totalDistance = 0;
        double totalTss = 0;

        for (WorkoutBlock block : training.getBlocks()) {
            double intensity = getBlockIntensity(block);
            int blockDuration;
            int blockDistance;

            if (block.durationSeconds() != null && block.durationSeconds() > 0) {
                blockDuration = block.durationSeconds();
                blockDistance = estimateDistance(blockDuration, intensity, sport, ftpPaceSecPerKm, cssSecPer100m);
            } else if (block.distanceMeters() != null && block.distanceMeters() > 0) {
                blockDistance = block.distanceMeters();
                blockDuration = estimateDuration(blockDistance, intensity, sport, ftpPaceSecPerKm, cssSecPer100m);
            } else {
                continue;
            }

            totalDurationSeconds += blockDuration;
            totalDistance += blockDistance;
            if (intensity > 0) {
                totalTss += TssCalculator.computeTss(blockDuration, intensity / 100.0);
            }
        }

        return new MetricsResult(totalTss, totalDurationSeconds, totalDistance);
    }

    private double getBlockIntensity(WorkoutBlock block) {
        if (block.intensityStart() != null && block.intensityStart() > 0
                && block.intensityEnd() != null && block.intensityEnd() > 0) {
            return (block.intensityStart() + block.intensityEnd()) / 2.0;
        }
        return block.intensityTarget() != null && block.intensityTarget() > 0 ? block.intensityTarget() : 50.0;
    }

    private double estimateSpeed(SportType sport, double intensity, int ftpPaceSecPerKm, int cssSecPer100m) {
        return switch (sport) {
            case RUNNING -> (1000.0 / ftpPaceSecPerKm) * (intensity / 100.0);
            case SWIMMING -> (100.0 / cssSecPer100m) * (intensity / 100.0);
            case CYCLING, BRICK -> sport.getTypicalSpeedMps() * Math.sqrt(intensity / 100.0);
        };
    }

    private int estimateDuration(int distanceMeters, double intensity, SportType sport,
                                  int ftpPaceSecPerKm, int cssSecPer100m) {
        if (intensity <= 0) return 0;
        double speedMps = estimateSpeed(sport, intensity, ftpPaceSecPerKm, cssSecPer100m);
        return speedMps > 0 ? (int) Math.round(distanceMeters / speedMps) : 0;
    }

    private int estimateDistance(int durationSeconds, double intensity, SportType sport,
                                  int ftpPaceSecPerKm, int cssSecPer100m) {
        if (intensity <= 0) return 0;
        double speedMps = estimateSpeed(sport, intensity, ftpPaceSecPerKm, cssSecPer100m);
        return (int) Math.round(durationSeconds * speedMps);
    }
}
