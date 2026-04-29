package com.koval.trainingplannerbackend.club.gazette;

import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;

public interface ClubGazettePostRepository extends MongoRepository<ClubGazettePost, String> {

    List<ClubGazettePost> findByEditionIdOrderByCreatedAtAsc(String editionId);

    List<ClubGazettePost> findByEditionIdAndAuthorIdOrderByCreatedAtAsc(String editionId, String authorId);

    long countByEditionId(String editionId);

    long countByEditionIdAndAuthorIdNot(String editionId, String authorId);

    void deleteByEditionId(String editionId);
}
