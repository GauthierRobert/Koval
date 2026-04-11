package com.koval.trainingplannerbackend.chat.dto;

import com.koval.trainingplannerbackend.chat.ChatRoomScope;

import java.time.Instant;

/** Full room detail returned when opening a single room. */
public record ChatRoomResponse(
        String id,
        ChatRoomScope scope,
        String clubId,
        String scopeRefId,
        String title,
        boolean joinable,
        boolean archived,
        Instant createdAt,
        String createdBy,
        Instant lastMessageAt,
        boolean currentUserIsMember,
        boolean currentUserMuted,
        Instant currentUserLastReadAt,
        String otherUserId
) {}
