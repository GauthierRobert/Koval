package com.koval.trainingplannerbackend.auth;

import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface UserRepository extends MongoRepository<User, String> {

    Optional<User> findByStravaId(String stravaId);

    Optional<User> findByEmail(String email);

    List<User> findByCoachId(String coachId);

    List<User> findByRole(UserRole role);

    List<User> findByCoachIdAndTagsContaining(String coachId, String tag);
}
