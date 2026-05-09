package com.koval.trainingplannerbackend.club.test;

import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;

public interface ClubTestRepository extends MongoRepository<ClubTest, String> {
    List<ClubTest> findByClubIdAndArchivedFalseOrderByCreatedAtDesc(String clubId);
    List<ClubTest> findByClubIdOrderByCreatedAtDesc(String clubId);
}
