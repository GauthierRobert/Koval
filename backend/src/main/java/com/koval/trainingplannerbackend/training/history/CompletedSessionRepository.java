package com.koval.trainingplannerbackend.training.history;

import org.springframework.data.mongodb.repository.MongoRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface CompletedSessionRepository extends MongoRepository<CompletedSession, String> {
    List<CompletedSession> findByUserIdOrderByCompletedAtDesc(String userId);
    List<CompletedSession> findByUserIdOrderByCompletedAtAsc(String userId);
    List<CompletedSession> findByUserIdAndCompletedAtBetween(
            String userId, LocalDateTime from, LocalDateTime to);
    Optional<CompletedSession> findByScheduledWorkoutId(String scheduledWorkoutId);
}
