package com.koval.trainingplannerbackend.chat.dto;

public record PostMessageRequest(
        String content,
        String clientNonce
) {}
