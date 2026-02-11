package com.koval.trainingplannerbackend.coach;

import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;

@Repository
public interface ScheduledWorkoutRepository extends MongoRepository<ScheduledWorkout, String> {

    List<ScheduledWorkout> findByAthleteId(String athleteId);

    List<ScheduledWorkout> findByAthleteIdAndScheduledDateBetween(
            String athleteId, LocalDate start, LocalDate end);

    List<ScheduledWorkout> findByAthleteIdAndStatus(String athleteId, ScheduleStatus status);

    List<ScheduledWorkout> findByAssignedBy(String coachId);

    List<ScheduledWorkout> findByTrainingId(String trainingId);

    List<ScheduledWorkout> findByAthleteIdAndScheduledDate(String athleteId, LocalDate date);
}
