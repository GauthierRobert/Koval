package com.koval.trainingplannerbackend.club;

import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;

public interface ClubTrainingSessionRepository extends MongoRepository<ClubTrainingSession, String> {
    List<ClubTrainingSession> findByClubIdOrderByScheduledAtDesc(String clubId);
}
