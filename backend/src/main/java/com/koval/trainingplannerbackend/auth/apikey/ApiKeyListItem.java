package com.koval.trainingplannerbackend.auth.apikey;

import java.time.Instant;

/** Summary for listing keys — never includes the full key. */
public record ApiKeyListItem(String id, String prefix, String name, Instant createdAt, Instant lastUsedAt) {

    public static ApiKeyListItem from(ApiKey key) {
        return new ApiKeyListItem(key.getId(), key.getPrefix(), key.getName(),
                key.getCreatedAt(), key.getLastUsedAt());
    }
}
