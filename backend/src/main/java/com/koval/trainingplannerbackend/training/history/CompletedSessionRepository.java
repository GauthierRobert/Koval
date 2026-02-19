package com.koval.trainingplannerbackend.training.history;

import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;

public interface CompletedSessionRepository extends MongoRepository<CompletedSession, String> {
    List<CompletedSession> findByUserIdOrderByCompletedAtDesc(String userId);
}
