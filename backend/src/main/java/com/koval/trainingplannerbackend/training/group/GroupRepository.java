package com.koval.trainingplannerbackend.training.group;

import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/** Spring Data repository for {@link Group} documents in the {@code groups} collection. */
@Repository
public interface GroupRepository extends MongoRepository<Group, String> {

    List<Group> findByCoachId(String coachId);

    Optional<Group> findByCoachIdAndName(String coachId, String name);

    List<Group> findByAthleteIdsContaining(String athleteId);

    boolean existsByAthleteIdsContaining(String athleteId);

    List<Group> findByIdIn(List<String> ids);
}
