package com.koval.trainingplannerbackend.oauth;

import lombok.Getter;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

@Getter
@Setter
@Document(collection = "oauth_authorization_codes")
public class AuthorizationCode {
    @Id
    private String id;

    @Indexed(unique = true)
    private String codeHash;

    private String clientId;
    private String userId;
    private String redirectUri;
    private String codeChallenge;
    private String codeChallengeMethod;

    @Indexed(expireAfter = "0s")
    private Instant expiresAt;

    private Boolean used;
}
