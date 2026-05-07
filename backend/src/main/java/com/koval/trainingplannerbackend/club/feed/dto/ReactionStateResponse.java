package com.koval.trainingplannerbackend.club.feed.dto;

public record ReactionStateResponse(
        String feedEventId,
        String commentId,
        String emoji,
        int count,
        boolean userReacted) {}
