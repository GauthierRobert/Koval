package com.koval.trainingplannerbackend.media.dto;

public record MediaVariantResponse(
        String url,
        String contentType,
        int width,
        int height,
        long sizeBytes
) {}
