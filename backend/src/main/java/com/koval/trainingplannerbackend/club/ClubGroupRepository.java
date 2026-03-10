package com.koval.trainingplannerbackend.club;

import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;
import java.util.Optional;

public interface ClubGroupRepository extends MongoRepository<ClubGroup, String> {
    List<ClubGroup> findByClubId(String clubId);
    Optional<ClubGroup> findByClubIdAndName(String clubId, String name);
    List<ClubGroup> findByClubIdAndMemberIdsContaining(String clubId, String userId);
}
