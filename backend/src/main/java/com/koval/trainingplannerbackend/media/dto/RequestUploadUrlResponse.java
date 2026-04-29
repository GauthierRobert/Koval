package com.koval.trainingplannerbackend.media.dto;

import java.time.LocalDateTime;

public record RequestUploadUrlResponse(
        String mediaId,
        String objectName,
        String signedUrl,
        String contentType,
        LocalDateTime expiresAt
) {}
