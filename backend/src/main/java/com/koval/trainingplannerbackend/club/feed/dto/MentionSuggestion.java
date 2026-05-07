package com.koval.trainingplannerbackend.club.feed.dto;

public record MentionSuggestion(
        String userId,
        String displayName,
        String profilePicture,
        String role) {}
