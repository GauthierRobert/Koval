package com.koval.trainingplannerbackend.club.feed.dto;

import java.util.List;

public record CreateAnnouncementRequest(String content, List<String> mediaIds, List<String> mentionUserIds) {
    public CreateAnnouncementRequest(String content, List<String> mediaIds) {
        this(content, mediaIds, List.of());
    }
}
