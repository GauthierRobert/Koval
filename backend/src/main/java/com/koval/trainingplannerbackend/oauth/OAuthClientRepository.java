package com.koval.trainingplannerbackend.oauth;

import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;
import java.util.Optional;

public interface OAuthClientRepository extends MongoRepository<OAuthClient, String> {
    Optional<OAuthClient> findByClientId(String clientId);
    List<OAuthClient> findByUserId(String userId);
    void deleteByIdAndUserId(String id, String userId);
}
