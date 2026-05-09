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

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Handles training enrichment: zone resolution, TSS/IF/duration/distance estimation.
 */
@Service
public class TrainingMetricsService {

    private static final int DEFAULT_FTP_PACE_SEC_PER_KM = 300;
    private static final int DEFAULT_CSS_SEC_PER_100M = 120;

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
        enrichTrainings(List.of(training), userId);
    }

    /**
     * Batch variant: fetches the user and athlete-coach IDs once, and memoises
     * zone-system lookups across trainings. Use this for list endpoints to avoid
     * an N+1 user/zone-system fetch per training.
     */
    public void enrichTrainings(List<Training> trainings, String userId) {
        if (trainings == null || trainings.isEmpty()) return;
        User user = userRepository.findById(userId).orElse(null);
        List<String> athleteCoachIds = groupService.getCoachIdsForAthlete(userId);
        Map<String, ZoneSystem> zoneCache = new HashMap<>();
        trainings.forEach(t -> enrichOne(t, userId, user, athleteCoachIds, zoneCache));
    }

    private void enrichOne(Training training, String userId, User user,
                           List<String> athleteCoachIds, Map<String, ZoneSystem> zoneCache) {
        if (training.getBlocks() == null || training.getBlocks().isEmpty()) return;
        resolveZoneTargetsCached(training, userId, athleteCoachIds, zoneCache);
        if (user != null) applyMetricsForUser(training, user);
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
        userRepository.findById(userId).ifPresent(user -> applyMetricsForUser(training, user));
    }

    private void applyMetricsForUser(Training training, User user) {
        SportType sport = Optional.ofNullable(training.getSportType()).orElse(SportType.CYCLING);
        applyMetricsResult(training, calculateBlocksMetrics(training, user, sport));
    }

    private void applyMetricsResult(Training training, MetricsResult result) {
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

    private void resolveZoneTargetsCached(Training training, String userId,
                                          List<String> athleteCoachIds,
                                          Map<String, ZoneSystem> zoneCache) {
        if (!hasZoneTargetsRecursive(training.getBlocks())) return;

        ZoneSystem zoneSystem = zoneCache.computeIfAbsent(
                zoneCacheKey(training, userId),
                k -> resolveZoneSystem(training, userId, athleteCoachIds));
        if (zoneSystem == null || zoneSystem.getZones() == null || zoneSystem.getZones().isEmpty()) return;

        Map<String, ZoneResolution> zoneMap = buildZoneMap(zoneSystem);
        training.setBlocks(training.getBlocks().stream()
                .map(block -> resolveElementZones(block, zoneMap))
                .toList());
    }

    private static String zoneCacheKey(Training training, String userId) {
        if (training.getZoneSystemId() != null && !training.getZoneSystemId().isBlank()) {
            return "id:" + training.getZoneSystemId();
        }
        SportType sport = Optional.ofNullable(training.getSportType()).orElse(SportType.CYCLING);
        String createdBy = Optional.ofNullable(training.getCreatedBy()).orElse(userId);
        return "default:" + createdBy + ":" + sport;
    }

    private Map<String, ZoneResolution> buildZoneMap(ZoneSystem zoneSystem) {
        return zoneSystem.getZones().stream()
                .filter(z -> z.label() != null)
                .collect(Collectors.toMap(
                        z -> z.label().toUpperCase(),
                        z -> new ZoneResolution((z.low() + z.high()) / 2,
                                z.label() + " - " + Optional.ofNullable(z.description()).orElse("") + " (" + z.low() + "-" + z.high() + "%)"),
                        (a, b) -> a));
    }

    private boolean hasZoneTargetsRecursive(List<WorkoutElement> elements) {
        if (elements == null) return false;
        return elements.stream().anyMatch(e -> e.isSet()
                ? hasZoneTargetsRecursive(e.elements())
                : e.zoneTarget() != null && !e.zoneTarget().isBlank());
    }

    private WorkoutElement resolveElementZones(WorkoutElement element, Map<String, ZoneResolution> zoneMap) {
        if (element.isSet()) {
            var resolvedChildren = element.elements().stream()
                    .map(child -> resolveElementZones(child, zoneMap))
                    .toList();
            return element.withElements(resolvedChildren);
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

    private ZoneSystem resolveZoneSystem(Training training, String userId, List<String> athleteCoachIds) {
        // 1. Try training's explicit zone system
        if (training.getZoneSystemId() != null && !training.getZoneSystemId().isBlank()) {
            try {
                return zoneSystemService.getZoneSystem(training.getZoneSystemId());
            } catch (Exception ignored) {}
        }

        SportType sport = Optional.ofNullable(training.getSportType()).orElse(SportType.CYCLING);
        // 2. Default for the training creator, else first match across the requesting athlete's coaches.
        String createdBy = Optional.ofNullable(training.getCreatedBy()).orElse(userId);
        return zoneSystemService.getDefaultZoneSystem(createdBy, sport)
                .orElseGet(() -> athleteCoachIds.stream()
                        .map(coachId -> zoneSystemService.getDefaultZoneSystem(coachId, sport))
                        .flatMap(Optional::stream)
                        .findFirst()
                        .orElse(null));
    }

    // ── Block-level metrics ─────────────────────────────────────────────────

    private record MetricsResult(double totalTss, int totalDurationSeconds, int totalDistance) {}

    private record BlockMeasure(int duration, int distance) {}

    private MetricsResult calculateBlocksMetrics(Training training, User user, SportType sport) {
        int ftpPaceSecPerKm = Optional.ofNullable(user.getFunctionalThresholdPace()).orElse(DEFAULT_FTP_PACE_SEC_PER_KM);
        int cssSecPer100m = Optional.ofNullable(user.getCriticalSwimSpeed()).orElse(DEFAULT_CSS_SEC_PER_100M);

        int totalDurationSeconds = 0;
        int totalDistance = 0;
        double totalTss = 0;

        List<WorkoutElement> flatBlocks = WorkoutElementFlattener.flatten(training.getBlocks());
        for (WorkoutElement block : flatBlocks) {
            double intensity = getBlockIntensity(block);
            BlockMeasure measure = computeBlockMeasure(block, intensity, sport, ftpPaceSecPerKm, cssSecPer100m);
            if (measure == null) {
                continue;
            }

            totalDurationSeconds += measure.duration();
            totalDistance += measure.distance();
            if (intensity > 0) {
                totalTss += TssCalculator.computeTss(measure.duration(), intensity / 100.0);
            }
        }

        return new MetricsResult(totalTss, totalDurationSeconds, totalDistance);
    }

    private BlockMeasure computeBlockMeasure(WorkoutElement block, double intensity, SportType sport,
                                             int ftpPaceSecPerKm, int cssSecPer100m) {
        if (block.durationSeconds() != null && block.durationSeconds() > 0) {
            int blockDuration = block.durationSeconds();
            int blockDistance = estimateDistance(blockDuration, intensity, sport, ftpPaceSecPerKm, cssSecPer100m);
            return new BlockMeasure(blockDuration, blockDistance);
        } else if (block.distanceMeters() != null && block.distanceMeters() > 0) {
            int blockDistance = block.distanceMeters();
            int blockDuration = estimateDuration(blockDistance, intensity, sport, ftpPaceSecPerKm, cssSecPer100m);
            return new BlockMeasure(blockDuration, blockDistance);
        }
        return null;
    }

    private double getBlockIntensity(WorkoutElement block) {
        if (block.intensityStart() != null && block.intensityStart() > 0
                && block.intensityEnd() != null && block.intensityEnd() > 0) {
            return (block.intensityStart() + block.intensityEnd()) / 2.0;
        }
        return Optional.ofNullable(block.intensityTarget()).filter(i -> i > 0).map(Integer::doubleValue).orElse(50.0);
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
