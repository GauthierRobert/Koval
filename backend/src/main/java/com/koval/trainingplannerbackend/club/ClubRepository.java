package com.koval.trainingplannerbackend.club;

import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;

public interface ClubRepository extends MongoRepository<Club, String> {
    List<Club> findByVisibility(ClubVisibility visibility);
}
