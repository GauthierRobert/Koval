package com.example.trainingplannerbackend.training;

import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface TrainingRepository extends MongoRepository<Training, String> {

    List<Training> findByCreatedBy(String userId);

    List<Training> findByCreatedByAndVisibility(String userId, TrainingVisibility visibility);

    List<Training> findByVisibility(TrainingVisibility visibility);

    List<Training> findByTagsContaining(String tag);
}
