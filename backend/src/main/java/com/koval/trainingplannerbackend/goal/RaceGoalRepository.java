package com.koval.trainingplannerbackend.goal;

import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;

public interface RaceGoalRepository extends MongoRepository<RaceGoal, String> {
    List<RaceGoal> findByAthleteIdOrderByRaceDateAsc(String athleteId);
}
