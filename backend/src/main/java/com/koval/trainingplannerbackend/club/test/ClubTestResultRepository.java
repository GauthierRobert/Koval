package com.koval.trainingplannerbackend.club.test;

import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;
import java.util.Optional;

public interface ClubTestResultRepository extends MongoRepository<ClubTestResult, String> {
    List<ClubTestResult> findByIterationId(String iterationId);
    Optional<ClubTestResult> findByIterationIdAndAthleteId(String iterationId, String athleteId);
    List<ClubTestResult> findByTestIdAndAthleteIdOrderByCreatedAtDesc(String testId, String athleteId);
    long countByTestId(String testId);
    long countByIterationId(String iterationId);
    void deleteByIterationId(String iterationId);
    void deleteByTestId(String testId);
}
