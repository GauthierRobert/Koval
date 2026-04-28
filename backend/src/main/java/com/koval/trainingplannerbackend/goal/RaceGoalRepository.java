package com.koval.trainingplannerbackend.goal;

import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;

public interface RaceGoalRepository extends MongoRepository<RaceGoal, String> {
    List<RaceGoal> findByAthleteId(String athleteId);
    List<RaceGoal> findByAthleteIdIn(List<String> athleteIds);
    boolean existsByAthleteIdAndRaceId(String athleteId, String raceId);
}
