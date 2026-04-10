package com.koval.trainingplannerbackend.club.feed.dto;

import com.koval.trainingplannerbackend.club.feed.ClubFeedEvent;

public record CommentUpdatePayload(
        String feedEventId,
        ClubFeedEvent.CommentEntry comment) {}
