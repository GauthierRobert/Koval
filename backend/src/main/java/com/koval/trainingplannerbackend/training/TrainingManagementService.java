package com.koval.trainingplannerbackend.training;

import com.koval.trainingplannerbackend.training.tag.TagService;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Service for Training CRUD operations.
 * These methods are designed to be exposed to the AI model via function
 * calling.
 */
@Service
public class TrainingManagementService {

    private final TrainingRepository trainingRepository;
    private final TagService tagService;

    public TrainingManagementService(TrainingRepository trainingRepository, TagService tagService) {
        this.trainingRepository = trainingRepository;
        this.tagService = tagService;
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
        Training saved = trainingRepository.save(training);
        tagService.registerTags(training.getTags(), userId);
        return saved;
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
     * AI Function: getTraining
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
     * Search trainings by tag.
     */
    @Tool(description = "Search training plans by tag name")
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
     * Discover trainings available to a user based on their tags.
     * PUBLIC tags: include all public trainings with that tag.
     * PRIVATE tags: include trainings with that tag only if created by the user or their coach.
     */
    public List<Training> discoverTrainingsByUserTags(List<String> userTags, String userId, String coachId) {
        if (userTags == null || userTags.isEmpty()) {
            return List.of();
        }

        Set<Training> result = new LinkedHashSet<>();
        List<Training> taggedTrainings = trainingRepository.findByTagsIn(userTags);

        for (Training training : taggedTrainings) {
            for (String tag : training.getTags()) {
                if (!userTags.contains(tag)) continue;

                boolean isPublicTag = tagService.isTagPublic(tag);
                if (isPublicTag && training.getVisibility() == TrainingVisibility.PUBLIC) {
                    result.add(training);
                } else if (!isPublicTag) {
                    // Private tag: only include if created by user or user's coach
                    if (userId.equals(training.getCreatedBy()) ||
                            (coachId != null && coachId.equals(training.getCreatedBy()))) {
                        result.add(training);
                    }
                }
            }
        }

        return new ArrayList<>(result);
    }
}
