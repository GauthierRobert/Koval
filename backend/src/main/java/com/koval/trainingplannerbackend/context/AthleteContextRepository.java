package com.koval.trainingplannerbackend.context;

import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.Optional;

public interface AthleteContextRepository extends MongoRepository<AthleteContext, String> {

    Optional<AthleteContext> findByAthleteIdAndAuthorId(String athleteId, String authorId);
}
