package com.koval.trainingplannerbackend.club.feed.dto;

public record CompletionUpdatePayload(
        String feedEventId,
        String clubSessionId,
        int completionCount,
        LatestCompletion latestCompletion) {

    public record LatestCompletion(
            String userId,
            String displayName,
            String profilePicture) {}
}
