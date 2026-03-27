package com.koval.trainingplannerbackend.training.received;

import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;

/** Spring Data repository for {@link ReceivedTraining} documents. */
public interface ReceivedTrainingRepository extends MongoRepository<ReceivedTraining, String> {

    List<ReceivedTraining> findByAthleteId(String athleteId);

    boolean existsByAthleteIdAndTrainingId(String athleteId, String trainingId);

    List<ReceivedTraining> findByTrainingIdAndAthleteIdIn(String trainingId, List<String> athleteIds);
}
