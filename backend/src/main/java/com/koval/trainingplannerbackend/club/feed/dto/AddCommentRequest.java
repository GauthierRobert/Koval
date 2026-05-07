package com.koval.trainingplannerbackend.club.feed.dto;

import java.util.List;

public record AddCommentRequest(String content, List<String> mentionUserIds) {
    public AddCommentRequest(String content) {
        this(content, List.of());
    }
}
