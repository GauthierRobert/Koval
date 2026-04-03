package com.koval.trainingplannerbackend.oauth;

import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.Optional;

public interface AuthorizationCodeRepository extends MongoRepository<AuthorizationCode, String> {
    Optional<AuthorizationCode> findByCodeHash(String codeHash);
}
