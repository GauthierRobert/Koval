package com.koval.trainingplannerbackend.club.feed.dto;

import com.koval.trainingplannerbackend.club.feed.ClubFeedEvent;
import com.koval.trainingplannerbackend.media.dto.MediaResponse;

import java.time.LocalDateTime;
import java.util.function.Function;

/**
 * A file (image or document) attached to a coach announcement at creation
 * time, with the underlying Media already resolved to signed URLs.
 */
public record AnnouncementAttachmentResponse(
        String id,
        LocalDateTime addedAt,
        MediaResponse file
) {
    public static AnnouncementAttachmentResponse from(ClubFeedEvent.AnnouncementAttachment a,
                                                      Function<String, MediaResponse> mediaResolver) {
        MediaResponse file = mediaResolver != null ? mediaResolver.apply(a.mediaId()) : null;
        return new AnnouncementAttachmentResponse(a.id(), a.addedAt(), file);
    }
}
