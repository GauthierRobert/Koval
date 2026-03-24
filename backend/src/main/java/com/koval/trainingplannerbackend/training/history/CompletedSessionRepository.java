package com.koval.trainingplannerbackend.training.history;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface CompletedSessionRepository extends MongoRepository<CompletedSession, String> {

    @Query(value = "{ 'userId': ?0, 'stravaActivityId': { $ne: null } }", fields = "{ 'stravaActivityId': 1 }")
    List<CompletedSession> findStravaActivityIdsByUserId(String userId);
    List<CompletedSession> findByUserIdOrderByCompletedAtDesc(String userId);
    Page<CompletedSession> findByUserIdOrderByCompletedAtDesc(String userId, Pageable pageable);
    List<CompletedSession> findByUserIdOrderByCompletedAtAsc(String userId);
    List<CompletedSession> findByUserIdAndCompletedAtBetween(
            String userId, LocalDateTime from, LocalDateTime to);
    Optional<CompletedSession> findByIdAndUserId(String id, String userId);
    Optional<CompletedSession> findByScheduledWorkoutId(String scheduledWorkoutId);
    List<CompletedSession> findByUserIdInAndCompletedAtBetween(
            List<String> userIds, LocalDateTime from, LocalDateTime to);
}
