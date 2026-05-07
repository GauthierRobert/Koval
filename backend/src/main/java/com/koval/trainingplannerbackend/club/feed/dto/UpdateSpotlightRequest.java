package com.koval.trainingplannerbackend.club.feed.dto;

import com.koval.trainingplannerbackend.club.feed.SpotlightBadge;

import java.util.List;

public record UpdateSpotlightRequest(
        String title,
        String message,
        SpotlightBadge badge,
        List<String> mediaIds,
        Integer expiresInDays,
        List<String> mentionUserIds) {}
