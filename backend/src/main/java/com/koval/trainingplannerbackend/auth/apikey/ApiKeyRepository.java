package com.koval.trainingplannerbackend.auth.apikey;

import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;
import java.util.Optional;

public interface ApiKeyRepository extends MongoRepository<ApiKey, String> {

    Optional<ApiKey> findByKeyHashAndActiveTrue(String keyHash);

    List<ApiKey> findByUserIdAndActiveTrueOrderByCreatedAtDesc(String userId);

    long countByUserIdAndActiveTrue(String userId);
}
