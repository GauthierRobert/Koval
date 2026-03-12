package com.koval.trainingplannerbackend.pacing;

import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;

public interface SimulationRequestRepository extends MongoRepository<SimulationRequest, String> {

    List<SimulationRequest> findByUserIdOrderByCreatedAtDesc(String userId);

    List<SimulationRequest> findByGoalIdOrderByCreatedAtDesc(String goalId);
}
