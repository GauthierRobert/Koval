package com.koval.trainingplannerbackend.club.membership;

import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;
import java.util.Optional;

public interface ClubMembershipRepository extends MongoRepository<ClubMembership, String> {
    List<ClubMembership> findByUserId(String userId);
    List<ClubMembership> findByClubId(String clubId);
    List<ClubMembership> findByClubIdAndStatus(String clubId, ClubMemberStatus status);
    Optional<ClubMembership> findByClubIdAndUserId(String clubId, String userId);
}
