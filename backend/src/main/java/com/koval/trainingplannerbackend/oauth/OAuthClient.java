package com.koval.trainingplannerbackend.oauth;

import lombok.Getter;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.List;

@Getter
@Setter
@Document(collection = "oauth_clients")
public class OAuthClient {
    @Id
    private String id;

    @Indexed(unique = true)
    private String clientId;

    private String clientSecretHash;
    private String clientName;
    private List<String> redirectUris;

    @Indexed
    private String userId;

    private Instant createdAt;
    private Instant lastUsedAt;
}
