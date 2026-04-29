package com.koval.trainingplannerbackend.club.feed.dto;

import com.koval.trainingplannerbackend.club.feed.ClubFeedEvent;
import com.koval.trainingplannerbackend.media.dto.MediaResponse;

import java.time.LocalDateTime;
import java.util.function.Function;

/**
 * A photo attached to a feed event by a member, with the underlying Media
 * already resolved (signed URLs + variants).
 */
public record PhotoEnrichmentResponse(
        String id,
        String contributedByUserId,
        String contributedByDisplayName,
        String contributedByProfilePicture,
        LocalDateTime addedAt,
        MediaResponse photo
) {
    public static PhotoEnrichmentResponse from(ClubFeedEvent.MediaEnrichment e,
                                               Function<String, MediaResponse> mediaResolver) {
        MediaResponse photo = mediaResolver != null ? mediaResolver.apply(e.mediaId()) : null;
        return new PhotoEnrichmentResponse(
                e.id(),
                e.contributedByUserId(),
                e.contributedByDisplayName(),
                e.contributedByProfilePicture(),
                e.addedAt(),
                photo);
    }
}
