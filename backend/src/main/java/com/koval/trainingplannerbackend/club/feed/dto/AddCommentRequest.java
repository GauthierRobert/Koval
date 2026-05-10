package com.koval.trainingplannerbackend.club.feed.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.List;

public record AddCommentRequest(
        @NotBlank @Size(max = 2000) String content,
        List<String> mentionUserIds
) {
    public AddCommentRequest(String content) {
        this(content, List.of());
    }
}
