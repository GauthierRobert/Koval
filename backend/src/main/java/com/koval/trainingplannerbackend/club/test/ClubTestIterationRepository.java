package com.koval.trainingplannerbackend.club.test;

import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;
import java.util.Optional;

public interface ClubTestIterationRepository extends MongoRepository<ClubTestIteration, String> {
    List<ClubTestIteration> findByTestIdOrderByCreatedAtDesc(String testId);
    Optional<ClubTestIteration> findByTestIdAndStatus(String testId, IterationStatus status);
    Optional<ClubTestIteration> findByTestIdAndLabel(String testId, String label);
    long countByTestId(String testId);
}
