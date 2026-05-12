package com.koval.trainingplannerbackend.training.history;

import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;

public interface AiAnalysisRepository extends MongoRepository<AiAnalysis, String> {

    List<AiAnalysis> findBySessionIdOrderByCreatedAtDesc(String sessionId);

    List<AiAnalysis> findByAthleteIdOrderByCreatedAtDesc(String athleteId);
}
