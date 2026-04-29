package com.koval.trainingplannerbackend.media.dto;

import com.koval.trainingplannerbackend.media.MediaProcessingStatus;

public record ConfirmUploadResponse(
        String mediaId,
        boolean confirmed,
        long sizeBytes,
        Integer width,
        Integer height,
        MediaProcessingStatus processingStatus,
        String blurHash
) {}
