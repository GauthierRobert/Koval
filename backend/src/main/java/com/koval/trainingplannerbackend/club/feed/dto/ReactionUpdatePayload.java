package com.koval.trainingplannerbackend.club.feed.dto;

public record ReactionUpdatePayload(
        String feedEventId,
        String commentId,
        String emoji,
        int count,
        String actorUserId,
        boolean added) {}
