package com.koval.trainingplannerbackend.chat.dto;

import com.koval.trainingplannerbackend.chat.ChatRoomScope;

import java.time.Instant;

/** Compact room entry for the sidebar: everything the room list needs, nothing more. */
public record ChatRoomSummaryResponse(
        String id,
        ChatRoomScope scope,
        String clubId,
        String scopeRefId,
        String title,
        boolean joinable,
        boolean muted,
        Instant lastMessageAt,
        String lastMessagePreview,
        String lastMessageSenderId,
        long unreadCount,
        /** For DMs: the other user's id, so the frontend can look up name/avatar. Null for non-DMs. */
        String otherUserId
) {}
