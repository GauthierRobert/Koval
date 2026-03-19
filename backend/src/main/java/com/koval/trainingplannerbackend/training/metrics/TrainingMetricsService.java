package com.koval.trainingplannerbackend.training.metrics;

import com.koval.trainingplannerbackend.auth.User;
import com.koval.trainingplannerbackend.auth.UserRepository;
import com.koval.trainingplannerbackend.training.group.GroupService;
import com.koval.trainingplannerbackend.training.model.SportType;
import com.koval.trainingplannerbackend.training.model.Training;
import com.koval.trainingplannerbackend.training.model.WorkoutElement;
import com.koval.trainingplannerbackend.training.model.WorkoutElementFlattener;
import com.koval.trainingplannerbackend.training.zone.ZoneSystem;
import com.koval.trainingplannerbackend.training.zone.ZoneSystemService;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Handles training enrichment: zone resolution, TSS/IF/duration/distance estimation.
 */
@Service
public class TrainingMetricsService {

    private final UserRepository userRepository;
    private final ZoneSystemService zoneSystemService;
    private final GroupService groupService;

    public TrainingMetricsService(UserRepository userRepository,
                                  ZoneSystemService zoneSystemService,
                                  GroupService groupService) {
        this.userRepository = userRepository;
        this.zoneSystemService = zoneSystemService;
        this.groupService = groupService;
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
     * Calculates estimated TSS and IF for the training based on user's thresholds.
     */
    public void calculateTrainingMetrics(Training training, String userId) {
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

    // ── Zone resolution ─────────────────────────────────────────────────────

    private void resolveZoneTargets(Training training, String userId) {
        boolean hasZoneTargets = hasZoneTargetsRecursive(training.getBlocks());
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

        List<WorkoutElement> resolvedBlocks = training.getBlocks().stream()
                .map(block -> resolveElementZones(block, zoneMap))
                .toList();

        training.setBlocks(resolvedBlocks);
    }

    private boolean hasZoneTargetsRecursive(List<WorkoutElement> elements) {
        if (elements == null) return false;
        for (WorkoutElement e : elements) {
            if (e.isSet()) {
                if (hasZoneTargetsRecursive(e.elements())) return true;
            } else if (e.zoneTarget() != null && !e.zoneTarget().isBlank()) {
                return true;
            }
        }
        return false;
    }

    private WorkoutElement resolveElementZones(WorkoutElement element, Map<String, ZoneResolution> zoneMap) {
        if (element.isSet()) {
            var resolvedChildren = element.elements().stream()
                    .map(child -> resolveElementZones(child, zoneMap))
                    .toList();
            return new WorkoutElement(element.repetitions(), resolvedChildren,
                    element.restDurationSeconds(), element.restIntensity(),
                    element.type(), element.durationSeconds(), element.distanceMeters(),
                    element.label(), element.description(), element.intensityTarget(),
                    element.intensityStart(), element.intensityEnd(), element.cadenceTarget(),
                    element.zoneTarget(), element.zoneLabel());
        }
        if (element.zoneTarget() != null && !element.zoneTarget().isBlank()
                && (element.intensityTarget() == null || element.intensityTarget() == 0)) {
            ZoneResolution resolution = zoneMap.get(element.zoneTarget().toUpperCase());
            if (resolution != null) {
                return element.withResolvedIntensity(resolution.midpoint(), resolution.displayLabel());
            }
        }
        return element;
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

    // ── Block-level metrics ─────────────────────────────────────────────────

    private record MetricsResult(double totalTss, int totalDurationSeconds, int totalDistance) {}

    private MetricsResult calculateBlocksMetrics(Training training, User user, SportType sport) {
        int ftpPaceSecPerKm = user.getFunctionalThresholdPace() != null ? user.getFunctionalThresholdPace() : 300;
        int cssSecPer100m = user.getCriticalSwimSpeed() != null ? user.getCriticalSwimSpeed() : 120;

        int totalDurationSeconds = 0;
        int totalDistance = 0;
        double totalTss = 0;

        List<WorkoutElement> flatBlocks = WorkoutElementFlattener.flatten(training.getBlocks());
        for (WorkoutElement block : flatBlocks) {
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

    private double getBlockIntensity(WorkoutElement block) {
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
