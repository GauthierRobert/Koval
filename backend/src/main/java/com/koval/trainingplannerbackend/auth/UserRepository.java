package com.koval.trainingplannerbackend.auth;

import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface UserRepository extends MongoRepository<User, String> {

    @Query("{ 'lastLogin': { $lt: ?0 }, 'fcmTokens.0': { $exists: true } }")
    List<User> findUsersWithStaleFcmTokens(LocalDateTime lastLoginBefore);


    Optional<User> findByStravaId(String stravaId);

    Optional<User> findByGoogleId(String googleId);

    Optional<User> findByGarminUserId(String garminUserId);

    Optional<User> findByZwiftUserId(String zwiftUserId);

    Optional<User> findByTerraUserId(String terraUserId);

    Optional<User> findByNolioUserId(String nolioUserId);

    Optional<User> findByEmail(String email);

    List<User> findByRole(UserRole role);

    List<User> findByIdIn(List<String> ids);
}
