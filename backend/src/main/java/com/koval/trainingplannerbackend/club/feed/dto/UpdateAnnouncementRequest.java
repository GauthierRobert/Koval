package com.koval.trainingplannerbackend.club.feed.dto;

import java.util.List;

public record UpdateAnnouncementRequest(String content, List<String> mediaIds, List<String> mentionUserIds) {
    public UpdateAnnouncementRequest(String content, List<String> mediaIds) {
        this(content, mediaIds, List.of());
    }
}
