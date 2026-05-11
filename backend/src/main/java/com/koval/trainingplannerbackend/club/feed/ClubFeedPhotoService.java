package com.koval.trainingplannerbackend.club.feed;

import com.koval.trainingplannerbackend.auth.User;
import com.koval.trainingplannerbackend.auth.UserService;
import com.koval.trainingplannerbackend.club.feed.dto.PhotoEnrichmentResponse;
import com.koval.trainingplannerbackend.club.membership.ClubAuthorizationService;
import com.koval.trainingplannerbackend.media.MediaPurpose;
import com.koval.trainingplannerbackend.media.MediaService;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Photo enrichments attached by members to existing feed events
 * (e.g. participants posting pictures on a session-completion event).
 */
@Service
public class ClubFeedPhotoService {

    private final ClubFeedEventRepository feedEventRepository;
    private final ClubAuthorizationService authorizationService;
    private final MediaService mediaService;
    private final UserService userService;
    private final ClubFeedSseBroadcaster broadcaster;
    private final ClubFeedMediaResolver mediaResolver;

    public ClubFeedPhotoService(ClubFeedEventRepository feedEventRepository,
                                ClubAuthorizationService authorizationService,
                                MediaService mediaService,
                                UserService userService,
                                ClubFeedSseBroadcaster broadcaster,
                                ClubFeedMediaResolver mediaResolver) {
        this.feedEventRepository = feedEventRepository;
        this.authorizationService = authorizationService;
        this.mediaService = mediaService;
        this.userService = userService;
        this.broadcaster = broadcaster;
        this.mediaResolver = mediaResolver;
    }

    /**
     * Attach one or more user-uploaded photos to a feed event. The mediaIds must
     * already be confirmed and have purpose FEED_POST_ENRICHMENT.
     */
    public List<PhotoEnrichmentResponse> attachPhotos(String userId, String clubId,
                                                      String feedEventId, List<String> mediaIds) {
        authorizationService.requireActiveMember(userId, clubId);
        if (mediaIds == null || mediaIds.isEmpty()) {
            throw new IllegalArgumentException("mediaIds is required");
        }
        ClubFeedEvent event = requireEvent(clubId, feedEventId);

        mediaService.requireOwnedAndConfirmed(userId, mediaIds, MediaPurpose.FEED_POST_ENRICHMENT);

        User author = userService.findById(userId).orElseThrow();
        List<PhotoEnrichmentResponse> added = new ArrayList<>();
        LocalDateTime now = LocalDateTime.now();
        for (String mediaId : mediaIds) {
            ClubFeedEvent.MediaEnrichment enrichment = new ClubFeedEvent.MediaEnrichment(
                    UUID.randomUUID().toString(),
                    mediaId,
                    userId,
                    author.getDisplayName(),
                    author.getProfilePicture(),
                    now);
            event.getPhotoEnrichments().add(enrichment);
            added.add(PhotoEnrichmentResponse.from(enrichment, mediaResolver::resolve));
        }
        event.setUpdatedAt(now);
        feedEventRepository.save(event);

        Map<String, Object> payload = new HashMap<>();
        payload.put("feedEventId", feedEventId);
        payload.put("enrichments", added);
        broadcaster.broadcast(clubId, "feed_photo_added", payload);

        return added;
    }

    /**
     * Detach a single enrichment. The author of the enrichment, or any
     * admin/coach of the club, can detach.
     */
    public void detachPhoto(String userId, String clubId, String feedEventId, String enrichmentId) {
        authorizationService.requireActiveMember(userId, clubId);
        ClubFeedEvent event = requireEvent(clubId, feedEventId);

        ClubFeedEvent.MediaEnrichment target = event.getPhotoEnrichments().stream()
                .filter(en -> en.id().equals(enrichmentId))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Photo enrichment not found"));

        boolean isAuthor = target.contributedByUserId().equals(userId);
        boolean isAdmin = authorizationService.isAdminOrCoach(userId, clubId);
        if (!isAuthor && !isAdmin) {
            throw new IllegalStateException("Only the contributor or an admin can remove this photo");
        }

        event.getPhotoEnrichments().removeIf(en -> en.id().equals(enrichmentId));
        event.setUpdatedAt(LocalDateTime.now());
        feedEventRepository.save(event);

        Map<String, Object> payload = new HashMap<>();
        payload.put("feedEventId", feedEventId);
        payload.put("enrichmentId", enrichmentId);
        broadcaster.broadcast(clubId, "feed_photo_removed", payload);
    }

    private ClubFeedEvent requireEvent(String clubId, String feedEventId) {
        return feedEventRepository.findById(feedEventId)
                .filter(e -> clubId.equals(e.getClubId()))
                .orElseThrow(() -> new IllegalArgumentException("Feed event not found"));
    }
}
