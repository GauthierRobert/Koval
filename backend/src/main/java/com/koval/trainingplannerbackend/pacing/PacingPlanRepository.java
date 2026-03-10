package com.koval.trainingplannerbackend.pacing;

import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;

public interface PacingPlanRepository extends MongoRepository<PacingPlan, String> {
    List<PacingPlan> findByUserId(String userId);
}
