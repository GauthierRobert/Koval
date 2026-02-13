package com.koval.trainingplannerbackend.coach;

import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface InviteCodeRepository extends MongoRepository<InviteCode, String> {

    Optional<InviteCode> findByCode(String code);

    List<InviteCode> findByCoachId(String coachId);
}
