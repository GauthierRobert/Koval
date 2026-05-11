package com.koval.trainingplannerbackend.club.feed;

import com.koval.trainingplannerbackend.media.Media;
import com.koval.trainingplannerbackend.media.MediaService;
import com.koval.trainingplannerbackend.media.dto.MediaResponse;
import org.springframework.stereotype.Component;

/**
 * Tiny indirection around {@link MediaService} for use by feed-event response DTOs:
 * resolves a mediaId to its full read DTO when the media exists and is confirmed,
 * otherwise returns null. Existing as a separate bean so any feed-side service can
 * pass its {@link #resolve} method as a {@code Function<String, MediaResponse>}.
 */
@Component
public class ClubFeedMediaResolver {

    private final MediaService mediaService;

    public ClubFeedMediaResolver(MediaService mediaService) {
        this.mediaService = mediaService;
    }

    public MediaResponse resolve(String mediaId) {
        return mediaService.findById(mediaId)
                .filter(Media::isConfirmed)
                .map(mediaService::buildMediaResponse)
                .orElse(null);
    }
}
