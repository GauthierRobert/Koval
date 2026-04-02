package com.koval.trainingplannerbackend.auth.apikey;

import lombok.Getter;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

@Getter
@Setter
@Document(collection = "api_keys")
public class ApiKey {

    @Id
    private String id;

    @Indexed
    private String userId;

    @Indexed(unique = true)
    private String keyHash;

    /** First 8 chars of the raw key for display (e.g. "koval_ab"). */
    private String prefix;

    /** User-provided label (e.g. "Claude Desktop"). */
    private String name;

    private Instant createdAt;
    private Instant lastUsedAt;
    private boolean active = true;
}
