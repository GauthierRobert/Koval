package com.koval.trainingplannerbackend.club.feed.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.List;

public record CreateAnnouncementRequest(
        @NotBlank @Size(max = 5000) String content,
        List<String> mediaIds,
        List<String> mentionUserIds
) {
    public CreateAnnouncementRequest(String content, List<String> mediaIds) {
        this(content, mediaIds, List.of());
    }
}
