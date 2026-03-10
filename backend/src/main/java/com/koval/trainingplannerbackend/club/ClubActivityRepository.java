package com.koval.trainingplannerbackend.club;

import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;

public interface ClubActivityRepository extends MongoRepository<ClubActivity, String> {
    List<ClubActivity> findByClubIdOrderByOccurredAtDesc(String clubId, Pageable pageable);
}
