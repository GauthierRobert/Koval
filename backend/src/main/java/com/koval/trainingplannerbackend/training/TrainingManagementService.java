package com.koval.trainingplannerbackend.training;

import com.koval.trainingplannerbackend.auth.User;
import com.koval.trainingplannerbackend.auth.UserRepository;
import com.koval.trainingplannerbackend.training.tag.Tag;
import com.koval.trainingplannerbackend.training.tag.TagService;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
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
public class TrainingManagementService {

    private final TrainingRepository trainingRepository;
    private final TagService tagService;
    private final UserRepository userRepository;
    private final com.koval.trainingplannerbackend.training.zone.ZoneSystemService zoneSystemService;

    public TrainingManagementService(TrainingRepository trainingRepository, TagService tagService,
            UserRepository userRepository,
            com.koval.trainingplannerbackend.training.zone.ZoneSystemService zoneSystemService) {
        this.trainingRepository = trainingRepository;
        this.tagService = tagService;
        this.userRepository = userRepository;
        this.zoneSystemService = zoneSystemService;
    }

    /**
     * Create a new training workout.
     */
    public Training createTraining(Training training, String userId) {
        training.setCreatedBy(userId);
        training.setCreatedAt(LocalDateTime.now());
        if (training.getVisibility() == null) {
            training.setVisibility(TrainingVisibility.PRIVATE);
        }
        calculateTrainingMetrics(training, userId);
        return trainingRepository.save(training);
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
            training.setBlocks(updates.getBlocks());
        if (updates.getTags() != null)
            training.setTags(updates.getTags());
        if (updates.getVisibility() != null)
            training.setVisibility(updates.getVisibility());
        if (updates.getTrainingType() != null)
            training.setTrainingType(updates.getTrainingType());

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
     * List all public trainings.
     */
    public List<Training> listPublicTrainings() {
        return trainingRepository.findByVisibility(TrainingVisibility.PUBLIC);
    }

    /**
     * Search trainings by tag (tag ID).
     */
    public List<Training> searchByTag(String tag) {
        return trainingRepository.findByTagsContaining(tag);
    }

    /**
     * Search trainings by training type.
     */
    public List<Training> searchByType(TrainingType trainingType) {
        return trainingRepository.findByTrainingType(trainingType);
    }

    /**
     * Discover trainings available to an athlete based on their tags.
     * Uses TagService to find athlete's tags, then finds trainings with those tag
     * IDs.
     */
    public List<Training> discoverTrainingsByUserTags(String athleteId) {
        List<Tag> athleteTags = tagService.getTagsForAthlete(athleteId);
        if (athleteTags.isEmpty()) {
            return List.of();
        }

        List<String> tagIds = athleteTags.stream().map(Tag::getId).toList();
        return trainingRepository.findByTagsIn(tagIds);
    }

    /**
     * Get training folders for an athlete grouped by tag name.
     * Uses TagService to find athlete's tags, then finds trainings with those tag
     * IDs.
     */
    public Map<String, List<Training>> getTrainingFolders(String athleteId) {
        List<Tag> athleteTags = tagService.getTagsForAthlete(athleteId);
        if (athleteTags.isEmpty()) {
            return Map.of();
        }

        Map<String, List<Training>> folders = new java.util.HashMap<>();
        for (Tag tag : athleteTags) {
            List<Training> trainings = trainingRepository.findByTagsContaining(tag.getId());
            folders.put(tag.getName(), trainings);
        }

        return folders;
    }

    /**
     * Resolves zone labels to power targets for a training based on an athlete's
     * zones.
     * This modifies the training object in place (in memory).
     */
    public Training resolveTraining(Training training, String athleteId) {
        if (training.getBlocks() != null) {
            List<WorkoutBlock> resolved = training.getBlocks().stream()
                    .map(b -> zoneSystemService.resolveZoneForBlock(b, athleteId, training.getSportType()))
                    .toList();
            training.setBlocks(resolved);
        }
        return training;
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

        MetricsResult result = calculateBlocksMetrics(training.getBlocks(), user, sport);

        training.setEstimatedTss((int) Math.round(result.totalTss()));

        if (result.totalDurationSeconds() > 0) {
            double durationHours = result.totalDurationSeconds() / 3600.0;
            double estimatedIf = Math.sqrt(result.totalTss() / (durationHours * 100.0));
            training.setEstimatedIf(Math.round(estimatedIf * 100.0) / 100.0);
        } else {
            training.setEstimatedIf(0.0);
        }
    }

    private record MetricsResult(double totalTss, int totalDurationSeconds) {
    }

    private MetricsResult calculateBlocksMetrics(List<WorkoutBlock> blocks,
            User user, SportType sport) {
        double totalTss = 0;
        int totalDuration = 0;

        for (WorkoutBlock block : blocks) {
            MetricsResult res = calculateBlockMetrics(block, user, sport);
            totalTss += res.totalTss();
            totalDuration += res.totalDurationSeconds();
        }
        return new MetricsResult(totalTss, totalDuration);
    }

    private MetricsResult calculateBlockMetrics(WorkoutBlock block, User user,
            SportType sport) {
        int duration = block.durationSeconds();
        double intensityFactor = 0.0;

        // Determine effective sport for this block
        SportType effectiveSport = getSportType(block, sport);

        if (effectiveSport == SportType.RUNNING) {
            Integer targetPace = block.paceTargetSecondsPerKm();
            Integer thresholdPace = user.getFunctionalThresholdPace();
            if (targetPace != null && thresholdPace != null && targetPace > 0) {
                intensityFactor = (double) thresholdPace / targetPace;
            } else if (block.paceStartSecondsPerKm() != null && block.paceEndSecondsPerKm() != null
                    && thresholdPace != null) {
                double avgPace = (block.paceStartSecondsPerKm() + block.paceEndSecondsPerKm()) / 2.0;
                if (avgPace > 0) {
                    intensityFactor = (double) thresholdPace / avgPace;
                }
            }
        } else if (effectiveSport == SportType.SWIMMING) {
            Integer targetPace = block.swimPacePer100m();
            Integer css = user.getCriticalSwimSpeed();
            if (targetPace != null && css != null && targetPace > 0) {
                intensityFactor = (double) css / targetPace;
            }
        } else {
            Integer power = block.powerTargetPercent();
            if (power != null) {
                intensityFactor = power / 100.0;
            } else if (block.powerStartPercent() != null && block.powerEndPercent() != null) {
                double avgPower = (block.powerStartPercent() + block.powerEndPercent()) / 2.0;
                intensityFactor = avgPower / 100.0;
            }
        }

        double tss = 0;
        if (intensityFactor > 0) {
            tss = (duration * intensityFactor * intensityFactor) / 3600.0 * 100.0;
        }
        return new MetricsResult(tss, duration);
    }

    private static SportType getSportType(WorkoutBlock block, SportType sport) {
        SportType effectiveSport = sport;
        if (sport == SportType.BRICK) {
            if (block.powerTargetPercent() != null || block.powerStartPercent() != null) {
                effectiveSport = SportType.CYCLING;
            } else if (block.paceTargetSecondsPerKm() != null || block.paceStartSecondsPerKm() != null) {
                effectiveSport = SportType.RUNNING;
            } else if (block.swimPacePer100m() != null) {
                effectiveSport = SportType.SWIMMING;
            } else {
                effectiveSport = SportType.CYCLING; // Fallback
            }
        }
        return effectiveSport;
    }
}
