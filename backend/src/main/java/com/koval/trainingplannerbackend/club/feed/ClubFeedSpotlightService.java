package com.koval.trainingplannerbackend.club.feed;

import com.koval.trainingplannerbackend.auth.User;
import com.koval.trainingplannerbackend.auth.UserService;
import com.koval.trainingplannerbackend.club.feed.dto.ClubFeedEventResponse;
import com.koval.trainingplannerbackend.club.feed.dto.CreateSpotlightRequest;
import com.koval.trainingplannerbackend.club.feed.dto.UpdateSpotlightRequest;
import com.koval.trainingplannerbackend.club.membership.ClubAuthorizationService;
import com.koval.trainingplannerbackend.club.membership.ClubMemberStatus;
import com.koval.trainingplannerbackend.club.membership.ClubMembership;
import com.koval.trainingplannerbackend.club.membership.ClubMembershipRepository;
import com.koval.trainingplannerbackend.media.MediaPurpose;
import com.koval.trainingplannerbackend.media.MediaService;
import com.koval.trainingplannerbackend.notification.NotificationService;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Manages MEMBER_SPOTLIGHT feed events: a coach- or admin-curated highlight of a
 * club member, pinned at the top of the feed for a configurable expiry window.
 */
@Service
public class ClubFeedSpotlightService {

    private static final int DEFAULT_EXPIRES_IN_DAYS = 7;
    private static final int MAX_EXPIRES_IN_DAYS = 30;

    private final ClubFeedEventRepository feedEventRepository;
    private final ClubAuthorizationService authorizationService;
    private final ClubMembershipRepository membershipRepository;
    private final UserService userService;
    private final MediaService mediaService;
    private final NotificationService notificationService;
    private final ClubFeedSseBroadcaster broadcaster;
    private final MentionResolver mentionResolver;
    private final ClubFeedMediaResolver mediaResolver;

    public ClubFeedSpotlightService(ClubFeedEventRepository feedEventRepository,
                                    ClubAuthorizationService authorizationService,
                                    ClubMembershipRepository membershipRepository,
                                    UserService userService,
                                    MediaService mediaService,
                                    NotificationService notificationService,
                                    ClubFeedSseBroadcaster broadcaster,
                                    MentionResolver mentionResolver,
                                    ClubFeedMediaResolver mediaResolver) {
        this.feedEventRepository = feedEventRepository;
        this.authorizationService = authorizationService;
        this.membershipRepository = membershipRepository;
        this.userService = userService;
        this.mediaService = mediaService;
        this.notificationService = notificationService;
        this.broadcaster = broadcaster;
        this.mentionResolver = mentionResolver;
        this.mediaResolver = mediaResolver;
    }

    public ClubFeedEventResponse createSpotlight(String userId, String clubId, CreateSpotlightRequest req) {
        authorizationService.requireAdminOrCoach(userId, clubId);

        if (req.spotlightedUserId() == null || req.spotlightedUserId().isBlank()) {
            throw new IllegalArgumentException("spotlightedUserId is required");
        }
        if (req.title() == null || req.title().isBlank()) {
            throw new IllegalArgumentException("title is required");
        }
        if (req.badge() == null) {
            throw new IllegalArgumentException("badge is required");
        }
        requireActiveMember(clubId, req.spotlightedUserId());

        if (req.mediaIds() != null && !req.mediaIds().isEmpty()) {
            mediaService.requireOwnedAndConfirmed(userId, req.mediaIds(), MediaPurpose.ANNOUNCEMENT_ATTACHMENT);
        }

        User author = userService.findById(userId).orElseThrow();
        User honoree = userService.findById(req.spotlightedUserId()).orElseThrow();
        LocalDateTime now = LocalDateTime.now();
        int days = clampExpiresIn(req.expiresInDays());

        ClubFeedEvent event = new ClubFeedEvent();
        event.setClubId(clubId);
        event.setType(ClubFeedEventType.MEMBER_SPOTLIGHT);
        event.setAuthorId(userId);
        event.setAuthorName(author.getDisplayName());
        event.setAuthorProfilePicture(author.getProfilePicture());
        event.setAnnouncementContent(req.message());
        event.setSpotlightedUserId(honoree.getId());
        event.setSpotlightedDisplayName(honoree.getDisplayName());
        event.setSpotlightedProfilePicture(honoree.getProfilePicture());
        event.setSpotlightTitle(req.title());
        event.setSpotlightMessage(req.message());
        event.setSpotlightBadge(req.badge());
        event.setSpotlightExpiresAt(now.plusDays(days));
        event.setCreatedAt(now);
        event.setUpdatedAt(now);
        event.setPinned(true);

        if (req.mediaIds() != null) {
            for (String mediaId : req.mediaIds()) {
                event.getAnnouncementAttachments().add(new ClubFeedEvent.AnnouncementAttachment(
                        UUID.randomUUID().toString(), mediaId, now));
            }
        }

        // Unpin previous spotlight
        feedEventRepository.findFirstByClubIdAndTypeAndPinnedTrueOrderByCreatedAtDesc(
                clubId, ClubFeedEventType.MEMBER_SPOTLIGHT)
                .ifPresent(prev -> {
                    prev.setPinned(false);
                    feedEventRepository.save(prev);
                });

        feedEventRepository.save(event);

        // Resolve mentions after save (uses event id as context)
        List<ClubFeedEvent.MentionRef> mentions = mentionResolver.resolve(
                clubId, req.mentionUserIds(), MentionResolver.CONTEXT_SPOTLIGHT, event.getId());
        if (!mentions.isEmpty()) {
            event.setMentionRefs(mentions);
            feedEventRepository.save(event);
        }

        ClubFeedEventResponse response = ClubFeedEventResponse.from(event, mediaResolver::resolve);
        broadcaster.broadcast(clubId, "new_feed_event", response);

        // Notify the spotlighted user (priority)
        if (!honoree.getId().equals(userId)) {
            notificationService.sendToUsers(List.of(honoree.getId()),
                    "You're in the spotlight!",
                    author.getDisplayName() + " spotlighted you: " + truncate(req.title(), 100),
                    Map.of("type", "CLUB_SPOTLIGHT", "clubId", clubId, "feedEventId", event.getId()),
                    "clubSpotlight");
        }

        // Notify other active members
        Set<String> mentionedIds = mentionResolver.idsOf(mentions);
        List<String> otherMembers = activeMemberIds(clubId);
        otherMembers.remove(userId);
        otherMembers.remove(honoree.getId());
        otherMembers.removeAll(mentionedIds);
        if (!otherMembers.isEmpty()) {
            notificationService.sendToUsers(otherMembers,
                    "New club spotlight",
                    honoree.getDisplayName() + " — " + truncate(req.title(), 100),
                    Map.of("type", "CLUB_SPOTLIGHT", "clubId", clubId, "feedEventId", event.getId()),
                    "clubAnnouncement");
        }
        if (!mentionedIds.isEmpty()) {
            mentionedIds.remove(userId);
            mentionedIds.remove(honoree.getId());
            if (!mentionedIds.isEmpty()) {
                notificationService.sendToUsers(new ArrayList<>(mentionedIds),
                        "You were mentioned",
                        author.getDisplayName() + " mentioned you in a spotlight",
                        Map.of("type", "CLUB_MENTION", "clubId", clubId, "feedEventId", event.getId()),
                        "clubMention");
            }
        }

        return response;
    }

    public ClubFeedEventResponse updateSpotlight(String userId, String clubId, String feedEventId,
                                                 UpdateSpotlightRequest req) {
        authorizationService.requireAdminOrCoach(userId, clubId);

        ClubFeedEvent event = feedEventRepository.findById(feedEventId)
                .filter(e -> clubId.equals(e.getClubId()))
                .filter(e -> e.getType() == ClubFeedEventType.MEMBER_SPOTLIGHT)
                .orElseThrow(() -> new IllegalArgumentException("Spotlight not found"));

        // Author may always edit; OWNER may also edit anyone's spotlight.
        boolean isAuthor = userId.equals(event.getAuthorId());
        boolean isOwner = membershipRepository.findByClubIdAndUserId(clubId, userId)
                .map(m -> m.getRole().name().equals("OWNER"))
                .orElse(false);
        if (!isAuthor && !isOwner) {
            throw new IllegalStateException("Only the author or club owner can edit this spotlight");
        }

        LocalDateTime now = LocalDateTime.now();
        if (req.title() != null) event.setSpotlightTitle(req.title());
        if (req.message() != null) {
            event.setSpotlightMessage(req.message());
            event.setAnnouncementContent(req.message());
        }
        if (req.badge() != null) event.setSpotlightBadge(req.badge());
        if (req.expiresInDays() != null) {
            event.setSpotlightExpiresAt(event.getCreatedAt().plusDays(clampExpiresIn(req.expiresInDays())));
        }

        if (req.mediaIds() != null) {
            List<String> existing = event.getAnnouncementAttachments().stream()
                    .map(ClubFeedEvent.AnnouncementAttachment::mediaId).toList();
            List<String> newMedia = req.mediaIds().stream().filter(id -> !existing.contains(id)).toList();
            if (!newMedia.isEmpty()) {
                mediaService.requireOwnedAndConfirmed(userId, newMedia, MediaPurpose.ANNOUNCEMENT_ATTACHMENT);
            }
            List<ClubFeedEvent.AnnouncementAttachment> rebuilt = new ArrayList<>();
            for (String mediaId : req.mediaIds()) {
                ClubFeedEvent.AnnouncementAttachment match = event.getAnnouncementAttachments().stream()
                        .filter(a -> a.mediaId().equals(mediaId)).findFirst().orElse(null);
                rebuilt.add(match != null
                        ? match
                        : new ClubFeedEvent.AnnouncementAttachment(UUID.randomUUID().toString(), mediaId, now));
            }
            event.setAnnouncementAttachments(rebuilt);
        }

        if (req.mentionUserIds() != null) {
            event.setMentionRefs(mentionResolver.resolve(
                    clubId, req.mentionUserIds(), MentionResolver.CONTEXT_SPOTLIGHT, event.getId()));
        }

        event.setUpdatedAt(now);
        feedEventRepository.save(event);

        ClubFeedEventResponse response = ClubFeedEventResponse.from(event, mediaResolver::resolve);
        broadcaster.broadcast(clubId, "feed_event_updated", response);
        return response;
    }

    public void deleteSpotlight(String userId, String clubId, String feedEventId) {
        authorizationService.requireAdminOrCoach(userId, clubId);
        ClubFeedEvent event = feedEventRepository.findById(feedEventId)
                .filter(e -> clubId.equals(e.getClubId()))
                .filter(e -> e.getType() == ClubFeedEventType.MEMBER_SPOTLIGHT)
                .orElseThrow(() -> new IllegalArgumentException("Spotlight not found"));

        feedEventRepository.deleteById(feedEventId);

        Map<String, Object> payload = new HashMap<>();
        payload.put("feedEventId", feedEventId);
        broadcaster.broadcast(clubId, "feed_event_deleted", payload);
    }

    /** Internal: unpin spotlights whose expiry window has passed. Returns count unpinned. */
    public int unpinExpiredSpotlights(LocalDateTime cutoff) {
        List<ClubFeedEvent> expired = feedEventRepository.findByTypeAndPinnedTrueAndSpotlightExpiresAtBefore(
                ClubFeedEventType.MEMBER_SPOTLIGHT, cutoff);
        for (ClubFeedEvent event : expired) {
            event.setPinned(false);
            event.setUpdatedAt(LocalDateTime.now());
            feedEventRepository.save(event);
            broadcaster.broadcast(event.getClubId(), "feed_event_updated",
                    ClubFeedEventResponse.from(event, mediaResolver::resolve));
        }
        return expired.size();
    }

    private void requireActiveMember(String clubId, String userId) {
        membershipRepository.findByClubIdAndUserId(clubId, userId)
                .filter(m -> m.getStatus() == ClubMemberStatus.ACTIVE)
                .orElseThrow(() -> new IllegalArgumentException("Spotlighted user is not an active member"));
    }

    private List<String> activeMemberIds(String clubId) {
        return membershipRepository.findByClubIdAndStatus(clubId, ClubMemberStatus.ACTIVE)
                .stream().map(ClubMembership::getUserId).collect(Collectors.toList());
    }

    private static int clampExpiresIn(Integer requested) {
        if (requested == null) return DEFAULT_EXPIRES_IN_DAYS;
        if (requested < 1) return 1;
        return Math.min(requested, MAX_EXPIRES_IN_DAYS);
    }

    private static String truncate(String s, int maxLen) {
        if (s == null) return "";
        return s.length() <= maxLen ? s : s.substring(0, maxLen) + "...";
    }
}
