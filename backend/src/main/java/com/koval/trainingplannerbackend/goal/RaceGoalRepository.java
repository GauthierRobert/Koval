package com.koval.trainingplannerbackend.goal;

import org.springframework.data.mongodb.repository.MongoRepository;

import java.time.LocalDate;
import java.util.List;

public interface RaceGoalRepository extends MongoRepository<RaceGoal, String> {
    List<RaceGoal> findByAthleteIdOrderByRaceDateAsc(String athleteId);
    List<RaceGoal> findByAthleteIdInOrderByRaceDateAsc(List<String> athleteIds);
    List<RaceGoal> findByAthleteIdInAndRaceDateGreaterThanEqualOrderByRaceDateAsc(List<String> athleteIds, LocalDate fromDate);
    boolean existsByAthleteIdAndRaceId(String athleteId, String raceId);
}
