package com.koval.trainingplannerbackend.training.history;

import com.koval.trainingplannerbackend.config.exceptions.ResourceNotFoundException;
import com.koval.trainingplannerbackend.training.TrainingRepository;
import com.koval.trainingplannerbackend.training.model.Training;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Manages workout feedback (difficulty/enjoyment ratings and notes) on completed sessions,
 * and maintains aggregate rating statistics on the linked Training documents.
 */
@Service
public class FeedbackService {

    private final CompletedSessionRepository sessionRepository;
    private final TrainingRepository trainingRepository;

    public FeedbackService(CompletedSessionRepository sessionRepository,
                           TrainingRepository trainingRepository) {
        this.sessionRepository = sessionRepository;
        this.trainingRepository = trainingRepository;
    }

    /**
     * Submit feedback for a completed session and recompute the linked Training's aggregate ratings.
     */
    public CompletedSession submitFeedback(String sessionId, String userId,
                                           CompletedSession.Feedback feedback) {
        CompletedSession session = sessionRepository.findByIdAndUserId(sessionId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Session", sessionId));

        session.setFeedback(feedback);
        CompletedSession saved = sessionRepository.save(session);

        if (saved.getTrainingId() != null) {
            recomputeTrainingRatings(saved.getTrainingId());
        }

        return saved;
    }

    /**
     * Recompute aggregate ratings on a Training document from all sessions with feedback.
     */
    public void recomputeTrainingRatings(String trainingId) {
        List<CompletedSession> sessions = sessionRepository
                .findByTrainingIdAndFeedbackIsNotNull(trainingId);

        if (sessions.isEmpty()) return;

        double avgDifficulty = sessions.stream()
                .filter(s -> s.getFeedback().difficultyRating() != null)
                .mapToInt(s -> s.getFeedback().difficultyRating())
                .average().orElse(0);

        double avgEnjoyment = sessions.stream()
                .filter(s -> s.getFeedback().enjoymentRating() != null)
                .mapToInt(s -> s.getFeedback().enjoymentRating())
                .average().orElse(0);

        trainingRepository.findById(trainingId).ifPresent(training -> {
            training.setRatingStats(new Training.RatingStats(
                    Math.round(avgDifficulty * 10.0) / 10.0,
                    Math.round(avgEnjoyment * 10.0) / 10.0,
                    sessions.size()));
            trainingRepository.save(training);
        });
    }
}
