package com.koval.trainingplannerbackend.chat.dto;

import com.koval.trainingplannerbackend.chat.ChatMessageType;

import java.time.Instant;

public record ChatMessageResponse(
        String id,
        String roomId,
        String senderId,
        String senderDisplayName,
        String senderProfilePicture,
        String content,
        Instant createdAt,
        Instant editedAt,
        boolean deleted,
        ChatMessageType type
) {}
