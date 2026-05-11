package com.koval.trainingplannerbackend.training;

import com.koval.trainingplannerbackend.training.model.Training;
import com.koval.trainingplannerbackend.training.model.TrainingType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

/** Spring Data repository for {@link Training} documents in the {@code trainings} collection. */
@Repository
public interface TrainingRepository extends MongoRepository<Training, String> {

    List<Training> findByCreatedBy(String userId);

    Page<Training> findByCreatedBy(String userId, Pageable pageable);

    List<Training> findByGroupIdsContaining(String groupId);

    List<Training> findByTrainingType(TrainingType trainingType);

    List<Training> findByGroupIdsIn(List<String> groupIds);

    List<Training> findByClubIdsIn(List<String> clubIds);

    /**
     * Same filter as {@link #findByCreatedBy(String)} but excludes the heavy
     * {@code blocks} field at the MongoDB driver level. Returned Trainings have
     * an empty {@code blocks} list — only safe for summary mapping.
     */
    @Query(value = "{ 'createdBy': ?0 }", fields = "{ 'blocks': 0 }")
    List<Training> findSummariesByCreatedBy(String userId);

    @Query(value = "{ 'createdBy': ?0 }", fields = "{ 'blocks': 0 }")
    Page<Training> findSummariesByCreatedBy(String userId, Pageable pageable);

    @Query(value = "{ 'clubIds': { $in: ?0 } }", fields = "{ 'blocks': 0 }")
    List<Training> findSummariesByClubIdsIn(List<String> clubIds);
}
