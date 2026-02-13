package com.koval.trainingplannerbackend.auth;

import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface UserRepository extends MongoRepository<User, String> {

    Optional<User> findByStravaId(String stravaId);

    Optional<User> findByGoogleId(String googleId);

    Optional<User> findByEmail(String email);

    List<User> findByRole(UserRole role);

    List<User> findByIdIn(List<String> ids);
}
