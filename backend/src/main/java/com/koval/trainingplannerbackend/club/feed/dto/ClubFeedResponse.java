package com.koval.trainingplannerbackend.club.feed.dto;

import java.util.List;

public record ClubFeedResponse(
        List<ClubFeedEventResponse> pinned,
        List<ClubFeedEventResponse> items,
        int page,
        boolean hasMore) {}
