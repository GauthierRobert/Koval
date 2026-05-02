package com.koval.trainingplannerbackend.auth;

import java.util.Optional;

/**
 * DTO for Strava OAuth token responses, including the athlete profile fields
 * returned in the same payload. Email is fetched from a follow-up athlete API
 * call and added via {@link #withEmail(String)} since it is not present in the
 * token-exchange response itself.
 */
public record StravaTokenResponse(
        String accessToken,
        String refreshToken,
        Long expiresAt,
        String athleteId,
        String firstName,
        String lastName,
        String profilePicture,
        String email) {

    public StravaTokenResponse withEmail(String newEmail) {
        return new StravaTokenResponse(accessToken, refreshToken, expiresAt,
                athleteId, firstName, lastName, profilePicture, newEmail);
    }

    /**
     * Concatenates first and last name with a single space, treating null parts
     * as empty strings. Always returns a non-null string (may be just a space).
     */
    public String displayName() {
        return Optional.ofNullable(firstName).orElse("") + " "
                + Optional.ofNullable(lastName).orElse("");
    }
}
