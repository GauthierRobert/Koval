package com.koval.trainingplannerbackend.training;

import com.koval.trainingplannerbackend.training.model.Training;
import com.koval.trainingplannerbackend.training.model.TrainingType;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface TrainingRepository extends MongoRepository<Training, String> {

    List<Training> findByCreatedBy(String userId);

    List<Training> findByTagsContaining(String tag);

    List<Training> findByTrainingType(TrainingType trainingType);

    List<Training> findByTagsIn(List<String> tags);
}
