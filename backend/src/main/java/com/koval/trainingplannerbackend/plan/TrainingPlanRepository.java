package com.koval.trainingplannerbackend.plan;

import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface TrainingPlanRepository extends MongoRepository<TrainingPlan, String> {

    List<TrainingPlan> findByCreatedByOrderByCreatedAtDesc(String userId);

    List<TrainingPlan> findByCreatedByAndStatus(String userId, PlanStatus status);

    List<TrainingPlan> findByAthleteIdsContaining(String athleteId);
}
