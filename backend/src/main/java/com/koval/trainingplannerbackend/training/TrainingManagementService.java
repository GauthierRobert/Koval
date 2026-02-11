package com.koval.trainingplannerbackend.training;

import org.springframework.ai.tool.annotation.Tool;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Service for Training CRUD operations.
 * These methods are designed to be exposed to the AI model via function
 * calling.
 */
@Service
public class TrainingManagementService {

    private final TrainingRepository trainingRepository;

    public TrainingManagementService(TrainingRepository trainingRepository) {
        this.trainingRepository = trainingRepository;
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
    public List<Training> searchByTag(String tag) {
        return trainingRepository.findByTagsContaining(tag);
    }
}
