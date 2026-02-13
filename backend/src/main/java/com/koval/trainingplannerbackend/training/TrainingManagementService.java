package com.koval.trainingplannerbackend.training;

import com.koval.trainingplannerbackend.auth.UserRepository;
import com.koval.trainingplannerbackend.training.tag.Tag;
import com.koval.trainingplannerbackend.training.tag.TagService;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

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

    public TrainingManagementService(TrainingRepository trainingRepository, TagService tagService,
                                     UserRepository userRepository) {
        this.trainingRepository = trainingRepository;
        this.tagService = tagService;
        this.userRepository = userRepository;
    }

    /**
     * Create a new training workout.
     */
    @Tool(description = "Create a new training workout plan")
    public Training createTraining(Training training, String userId) {
        training.setCreatedBy(userId);
        training.setCreatedAt(LocalDateTime.now());
        if (training.getVisibility() == null) {
            training.setVisibility(TrainingVisibility.PRIVATE);
        }
        return trainingRepository.save(training);
    }

    /**
     * Update an existing training.
     */
    @Tool(description = "Update an existing training plan by its ID")
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

        return trainingRepository.save(training);
    }

    /**
     * Delete a training.
     */
    @Tool(description = "Delete a training plan by its ID")
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
    @Tool(description = "List all training plans created by a specific user")
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
    @Tool(description = "Search training plans by tag ID")
    public List<Training> searchByTag(String tag) {
        return trainingRepository.findByTagsContaining(tag);
    }

    /**
     * Search trainings by training type.
     */
    @Tool(description = "Search training plans by training type (VO2MAX, THRESHOLD, SWEET_SPOT, ENDURANCE, SPRINT, RECOVERY, MIXED, TEST)")
    public List<Training> searchByType(TrainingType trainingType) {
        return trainingRepository.findByTrainingType(trainingType);
    }

    /**
     * Discover trainings available to an athlete based on their tags.
     * Uses TagService to find athlete's tags, then finds trainings with those tag IDs.
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
     * Uses TagService to find athlete's tags, then finds trainings with those tag IDs.
     */
    public Map<String, List<Training>> getTrainingFolders(String athleteId) {
        List<Tag> athleteTags = tagService.getTagsForAthlete(athleteId);
        if (athleteTags.isEmpty()) {
            return Map.of();
        }

        Map<String, List<Training>> folders = new HashMap<>();
        for (Tag tag : athleteTags) {
            List<Training> trainings = trainingRepository.findByTagsContaining(tag.getId());
            folders.put(tag.getName(), trainings);
        }

        return folders;
    }
}
