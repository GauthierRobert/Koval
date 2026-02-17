package com.koval.trainingplannerbackend.training;

import com.koval.trainingplannerbackend.auth.User;
import com.koval.trainingplannerbackend.auth.UserRepository;
import com.koval.trainingplannerbackend.training.model.SportType;
import com.koval.trainingplannerbackend.training.model.Training;
import com.koval.trainingplannerbackend.training.model.TrainingType;
import com.koval.trainingplannerbackend.training.model.WorkoutBlock;
import com.koval.trainingplannerbackend.training.tag.Tag;
import com.koval.trainingplannerbackend.training.tag.TagService;
import com.koval.trainingplannerbackend.training.zone.ZoneSystemService;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Service for Training CRUD operations.
 * These methods are designed to be exposed to the AI model via function
 * calling.
 */
@Service
public class TrainingService {

    private final TrainingRepository trainingRepository;
    private final TagService tagService;
    private final UserRepository userRepository;

    public TrainingService(TrainingRepository trainingRepository,
                           TagService tagService,
                           UserRepository userRepository) {
        this.trainingRepository = trainingRepository;
        this.tagService = tagService;
        this.userRepository = userRepository;
    }

    /**
     * Create a new training workout.
     */
    public Training createTraining(Training training, String userId) {
        training.setCreatedBy(userId);
        training.setCreatedAt(LocalDateTime.now());
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
            training.setEstimatedDurationSeconds(result.totalDurationSeconds());
            training.setEstimatedDistance(result.totalDistance());
        } else {
            training.setEstimatedIf(0.0);
        }
    }

    private record MetricsResult(double totalTss, int totalDurationSeconds, int totalDistance) {
    }

    //TODO
    private MetricsResult calculateBlocksMetrics(List<WorkoutBlock> blocks,
            User user, SportType sport) {

        return new MetricsResult(0, 0, 0);
    }

    //TODO
    private MetricsResult calculateBlockMetrics(WorkoutBlock block, User user, SportType sport) {
       return new MetricsResult(0, 0, 0);
    }
}
