package com.koval.trainingplannerbackend.training;

import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Service for managing training and workout execution history.
 * Handles completed workouts, performance analytics, and historical data.
 */
@Service
public class HistoryService {

    private final TrainingRepository trainingRepository;

    public HistoryService(TrainingRepository trainingRepository) {
        this.trainingRepository = trainingRepository;
    }

    /**
     * Save a training to the database.
     */
    public Training saveTraining(Training training) {
        return trainingRepository.save(training);
    }

    /**
     * Get all trainings.
     */
    public List<Training> getAllTrainings() {
        return trainingRepository.findAll();
    }

    /**
     * Get trainings by user ID.
     */
    public List<Training> getTrainingsByUser(String userId) {
        return trainingRepository.findByCreatedBy(userId);
    }
}
