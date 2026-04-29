package com.koval.trainingplannerbackend.media.dto;

import com.koval.trainingplannerbackend.media.MediaPurpose;

public record RequestUploadUrlRequest(
        MediaPurpose purpose,
        String contentType,
        long sizeBytes,
        String clubId,
        String originalFileName
) {}
