package com.koval.trainingplannerbackend.training.tag;

import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface TagRepository extends MongoRepository<Tag, String> {

    List<Tag> findByCoachId(String coachId);

    Optional<Tag> findByCoachIdAndName(String coachId, String name);

    List<Tag> findByAthleteIdsContaining(String athleteId);

    boolean existsByAthleteIdsContaining(String athleteId);

    List<Tag> findByIdIn(List<String> ids);
}
