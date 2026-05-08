package com.koval.trainingplannerbackend.club.feed.dto;

import java.time.LocalDateTime;
import java.util.List;

public record EngagementInsightsResponse(
        List<MemberEngagement> members,
        int days,
        LocalDateTime generatedAt) {

    public record MemberEngagement(
            String userId,
            String displayName,
            String profilePicture,
            String role,
            int commentsPosted,
            int reactionsGiven,
            int sessionsCompleted,
            LocalDateTime lastActiveAt) {}
}
