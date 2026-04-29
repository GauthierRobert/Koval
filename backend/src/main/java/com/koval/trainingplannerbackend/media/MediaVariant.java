package com.koval.trainingplannerbackend.media;

/**
 * One resolution-resized copy of a {@link Media}'s original image.
 * Stored alongside the original in GCS under a {label}-suffixed object name.
 */
public record MediaVariant(
        String label,            // "thumb" | "small" | "medium" | "large"
        String objectName,       // GCS path
        String contentType,      // image/jpeg in v1 (WebP later)
        int width,
        int height,
        long sizeBytes
) {}
