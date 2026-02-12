package com.koval.trainingplannerbackend.training.tag;

import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface TagRepository extends MongoRepository<Tag, String> {

    Optional<Tag> findByName(String name);

    List<Tag> findByVisibility(TagVisibility visibility);

    List<Tag> findByCreatedBy(String userId);

    List<Tag> findByNameIn(List<String> names);
}
