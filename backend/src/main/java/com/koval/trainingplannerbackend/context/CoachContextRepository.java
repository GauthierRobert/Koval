package com.koval.trainingplannerbackend.context;

import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.Optional;

public interface CoachContextRepository extends MongoRepository<CoachContext, String> {

    Optional<CoachContext> findByCoachId(String coachId);
}
