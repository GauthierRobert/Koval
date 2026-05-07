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
import com.koval.trainingplannerbackend.training.model.BlockType;
import com.koval.trainingplannerbackend.training.model.SportType;
import com.koval.trainingplannerbackend.training.model.Training;
import com.koval.trainingplannerbackend.training.model.WorkoutElement;
import com.koval.trainingplannerbackend.training.zone.Zone;
import com.koval.trainingplannerbackend.training.zone.ZoneSystem;
import com.koval.trainingplannerbackend.training.zone.ZoneSystemService;
import com.koval.trainingplannerbackend.training.zone.ZoneUtils;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;

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
    private final NolioPushService nolioPushService;
    private final UserService userService;
    private final ObjectMapper objectMapper;
    private final ZoneSystemService zoneSystemService;

    public TrainingService(TrainingRepository trainingRepository,
                           TrainingMetricsService metricsService,
                           ClubMembershipRepository membershipRepository,
                           ZwiftWorkoutService zwiftWorkoutService,
                           NolioPushService nolioPushService,
                           UserService userService,
                           ObjectMapper objectMapper,
                           ZoneSystemService zoneSystemService) {
        this.trainingRepository = trainingRepository;
        this.metricsService = metricsService;
        this.membershipRepository = membershipRepository;
        this.zwiftWorkoutService = zwiftWorkoutService;
        this.nolioPushService = nolioPushService;
        this.userService = userService;
        this.objectMapper = objectMapper;
        this.zoneSystemService = zoneSystemService;
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
        ZoneSystem zoneSystem = resolveZoneSystem(training, userId);
        training.setBlocks(training.getBlocks().stream()
                .map(b -> standardizeBlockType(b, zoneSystem))
                .toList());
        Training saved = trainingRepository.save(training);
        zwiftWorkoutService.autoSyncIfEnabled(userId, saved);
        nolioPushService.autoSyncIfEnabled(userId, saved);
        return saved;
    }

    /**
     * Because AI can be Wrong — recurses into sets. When a leaf block has no
     * intensity, attempts to resolve it from a known zone (matched against the
     * training's custom zone system or the creator's default for the sport)
     * via {@code zoneTarget} or {@code label}; falls back to PAUSE if no zone
     * matches.
     */
    private WorkoutElement standardizeBlockType(WorkoutElement element, ZoneSystem zoneSystem) {
        if (element.isSet()) {
            var standardizedChildren = element.elements().stream()
                    .map(child -> standardizeBlockType(child, zoneSystem))
                    .toList();
            return element.withElements(standardizedChildren);
        }

        if (isPositive(element.intensityEnd()) && isPositive(element.intensityStart())) {
            return element.updateType(BlockType.RAMP);
        }

        // Preserve TRANSITION blocks — they intentionally have no intensity
        if (element.type() == BlockType.TRANSITION) {
            return element;
        }

        if (!isPositive(element.intensityEnd()) && !isPositive(element.intensityStart())
                && !isPositive(element.intensityTarget())) {
            Zone matched = matchZone(element, zoneSystem);
            if (matched != null) {
                int midpoint = (matched.low() + matched.high()) / 2;
                return element.withResolvedIntensity(midpoint, formatZoneDisplayLabel(matched));
            }
            // Unmatched zoneTarget is preserved for later enrichment (zone system may
            // change, or be absent at create time).
            if (element.zoneTarget() != null && !element.zoneTarget().isBlank()) {
                return element;
            }
            return element.updateType(BlockType.PAUSE);
        }
        return element;
    }

    private static boolean isPositive(Integer val) {
        return val != null && val > 0;
    }

    /**
     * Resolves the zone system attached to a training: the custom one referenced
     * by {@code zoneSystemId}, otherwise the creator's default for the sport, otherwise
     * the built-in fallback from {@link ZoneUtils}. Always non-null.
     */
    private ZoneSystem resolveZoneSystem(Training training, String userId) {
        if (training.getZoneSystemId() != null && !training.getZoneSystemId().isBlank()) {
            try {
                return zoneSystemService.getZoneSystem(training.getZoneSystemId());
            } catch (Exception ignored) {
                // fall through to default
            }
        }
        SportType sport = Optional.ofNullable(training.getSportType()).orElse(SportType.CYCLING);
        String createdBy = Optional.ofNullable(training.getCreatedBy()).orElse(userId);
        return zoneSystemService.getDefaultZoneSystem(createdBy, sport)
                .orElseGet(() -> ZoneUtils.getDefaultZoneSystem(sport));
    }

    /**
     * Tries to find a zone in {@code zoneSystem} matching the element's
     * {@code zoneTarget} (first) or {@code label} (fallback). Exact matches win
     * over token-contains matches (e.g. "Z4" inside "Z4 Threshold").
     */
    private static Zone matchZone(WorkoutElement element, ZoneSystem zoneSystem) {
        if (zoneSystem == null || zoneSystem.getZones() == null || zoneSystem.getZones().isEmpty()) {
            return null;
        }
        Zone matched = matchZoneInText(element.zoneTarget(), zoneSystem);
        if (matched != null) return matched;
        return matchZoneInText(element.label(), zoneSystem);
    }

    private static Zone matchZoneInText(String text, ZoneSystem zoneSystem) {
        if (text == null || text.isBlank()) return null;
        String upper = text.trim().toUpperCase();
        List<Zone> labeled = zoneSystem.getZones().stream()
                .filter(z -> z.label() != null && !z.label().isBlank())
                .toList();
        return labeled.stream()
                .filter(z -> upper.equals(z.label().trim().toUpperCase()))
                .findFirst()
                .or(() -> labeled.stream()
                        .filter(z -> containsAsToken(upper, z.label().trim().toUpperCase()))
                        .findFirst())
                .orElse(null);
    }

    /** Word-boundary contains: "Z4" matches "Z4 Threshold" but not "Z40". */
    private static boolean containsAsToken(String text, String token) {
        String regex = "(?:^|[^A-Z0-9])" + Pattern.quote(token) + "(?:[^A-Z0-9]|$)";
        return Pattern.compile(regex).matcher(text).find();
    }

    private static String formatZoneDisplayLabel(Zone z) {
        String desc = Optional.ofNullable(z.description())
                .filter(d -> !d.isBlank())
                .map(d -> " - " + d)
                .orElse("");
        return z.label() + desc + " (" + z.low() + "-" + z.high() + "%)";
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
            ZoneSystem zoneSystem = resolveZoneSystem(training, training.getCreatedBy());
            training.setBlocks(blocks.stream()
                    .map(b -> standardizeBlockType(b, zoneSystem))
                    .toList());
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
