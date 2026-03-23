package com.koval.trainingplannerbackend.club.invite;

import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ClubInviteCodeRepository extends MongoRepository<ClubInviteCode, String> {

    Optional<ClubInviteCode> findByCode(String code);

    List<ClubInviteCode> findByClubId(String clubId);
}
