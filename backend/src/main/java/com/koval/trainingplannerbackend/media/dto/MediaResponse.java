package com.koval.trainingplannerbackend.media.dto;

import com.koval.trainingplannerbackend.media.MediaProcessingStatus;
import com.koval.trainingplannerbackend.media.MediaPurpose;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * Full read-side representation of a Media. Includes the original signed URL
 * (fallback) plus a map of resolution variants (label → {url, dimensions}) so
 * the frontend can pick the right size via {@code <picture>} / {@code srcset}.
 *
 * The {@code blurHash} string lets the frontend render a blurred placeholder
 * instantly while real bytes load.
 */
public record MediaResponse(
        String mediaId,
        MediaPurpose purpose,
        String contentType,
        long sizeBytes,
        Integer width,
        Integer height,
        String blurHash,
        MediaProcessingStatus processingStatus,
        String originalUrl,
        Map<String, MediaVariantResponse> variants,
        LocalDateTime expiresAt
) {}
