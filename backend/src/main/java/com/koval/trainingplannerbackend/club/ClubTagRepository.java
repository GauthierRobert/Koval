package com.koval.trainingplannerbackend.club;

import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;
import java.util.Optional;

public interface ClubTagRepository extends MongoRepository<ClubTag, String> {
    List<ClubTag> findByClubId(String clubId);
    Optional<ClubTag> findByClubIdAndName(String clubId, String name);
    List<ClubTag> findByClubIdAndMemberIdsContaining(String clubId, String userId);
}
