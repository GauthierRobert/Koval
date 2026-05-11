package com.koval.trainingplannerbackend.club.feed;

import com.koval.trainingplannerbackend.auth.User;
import com.koval.trainingplannerbackend.auth.UserService;
import com.koval.trainingplannerbackend.club.feed.dto.ClubFeedEventResponse;
import com.koval.trainingplannerbackend.club.membership.ClubAuthorizationService;
import com.koval.trainingplannerbackend.club.membership.ClubMembershipService;
import com.koval.trainingplannerbackend.media.MediaPurpose;
import com.koval.trainingplannerbackend.media.MediaService;
import com.koval.trainingplannerbackend.notification.NotificationService;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Coach-announcement feed events: create / update / delete plus mention resolution
 * and push notifications.
 */
@Service
public class ClubAnnouncementService {

    private static final int NOTIFICATION_PREVIEW_CHARS = 100;

    private final ClubFeedEventRepository feedEventRepository;
    private final ClubAuthorizationService authorizationService;
    private final ClubMembershipService membershipService;
    private final UserService userService;
    private final NotificationService notificationService;
    private final ClubFeedSseBroadcaster broadcaster;
    private final MediaService mediaService;
    private final MentionResolver mentionResolver;
    private final ClubFeedMediaResolver mediaResolver;

    public ClubAnnouncementService(ClubFeedEventRepository feedEventRepository,
                                   ClubAuthorizationService authorizationService,
                                   ClubMembershipService membershipService,
                                   UserService userService,
                                   NotificationService notificationService,
                                   ClubFeedSseBroadcaster broadcaster,
                                   MediaService mediaService,
                                   MentionResolver mentionResolver,
                                   ClubFeedMediaResolver mediaResolver) {
        this.feedEventRepository = feedEventRepository;
        this.authorizationService = authorizationService;
        this.membershipService = membershipService;
        this.userService = userService;
        this.notificationService = notificationService;
        this.broadcaster = broadcaster;
        this.mediaService = mediaService;
        this.mentionResolver = mentionResolver;
        this.mediaResolver = mediaResolver;
    }

    /**
     * Create a coach announcement with optional attachments and @mentions.
     * Mentioned users receive a high-priority {@code clubMention} push; remaining
     * active members get a {@code clubAnnouncement} broadcast (deduped against the
     * mention set).
     */
    public ClubFeedEventResponse create(String userId, String clubId, String content,
                                        List<String> mediaIds, List<String> mentionUserIds) {
        authorizationService.requireAdminOrCoach(userId, clubId);

        if (mediaIds != null && !mediaIds.isEmpty()) {
            mediaService.requireOwnedAndConfirmed(userId, mediaIds, MediaPurpose.ANNOUNCEMENT_ATTACHMENT);
        }

        User author = userService.findById(userId).orElseThrow();
        LocalDateTime now = LocalDateTime.now();

        ClubFeedEvent event = new ClubFeedEvent();
        event.setClubId(clubId);
        event.setType(ClubFeedEventType.COACH_ANNOUNCEMENT);
        event.setAuthorId(userId);
        event.setAuthorName(author.getDisplayName());
        event.setAuthorProfilePicture(author.getProfilePicture());
        event.setAnnouncementContent(content);
        event.setCreatedAt(now);
        event.setUpdatedAt(now);
        if (mediaIds != null) {
            for (String mediaId : mediaIds) {
                event.getAnnouncementAttachments().add(new ClubFeedEvent.AnnouncementAttachment(
                        UUID.randomUUID().toString(), mediaId, now));
            }
        }

        // Resolve and persist mentions only after the event id is available as context.
        feedEventRepository.save(event);
        List<ClubFeedEvent.MentionRef> refs = mentionResolver.resolve(
                clubId, mentionUserIds, MentionResolver.CONTEXT_ANNOUNCEMENT, event.getId());
        if (!refs.isEmpty()) {
            event.setMentionRefs(refs);
            feedEventRepository.save(event);
        }

        ClubFeedEventResponse response = ClubFeedEventResponse.from(event, mediaResolver::resolve);
        broadcaster.broadcast(clubId, "new_feed_event", response);

        notifyMembers(clubId, userId, author, content, event.getId(), refs);
        return response;
    }

    /**
     * Edit an existing coach announcement. Only the original author may edit.
     * Replaces content and (if provided) the full attachment list.
     */
    public ClubFeedEventResponse update(String userId, String clubId, String feedEventId,
                                        String content, List<String> mediaIds,
                                        List<String> mentionUserIds) {
        authorizationService.requireActiveMember(userId, clubId);

        ClubFeedEvent event = requireAnnouncement(clubId, feedEventId);
        if (!userId.equals(event.getAuthorId())) {
            throw new IllegalStateException("Only the author can edit this announcement");
        }

        if (mediaIds != null && !mediaIds.isEmpty()) {
            // Only validate ownership for newly added media; existing attachments stay as-is.
            List<String> existing = event.getAnnouncementAttachments().stream()
                    .map(ClubFeedEvent.AnnouncementAttachment::mediaId).toList();
            List<String> newMedia = mediaIds.stream().filter(id -> !existing.contains(id)).toList();
            if (!newMedia.isEmpty()) {
                mediaService.requireOwnedAndConfirmed(userId, newMedia, MediaPurpose.ANNOUNCEMENT_ATTACHMENT);
            }
        }

        LocalDateTime now = LocalDateTime.now();
        event.setAnnouncementContent(content);
        event.setUpdatedAt(now);

        if (mediaIds != null) {
            List<ClubFeedEvent.AnnouncementAttachment> rebuilt = new ArrayList<>();
            for (String mediaId : mediaIds) {
                ClubFeedEvent.AnnouncementAttachment existing = event.getAnnouncementAttachments().stream()
                        .filter(a -> a.mediaId().equals(mediaId)).findFirst().orElse(null);
                rebuilt.add(existing != null
                        ? existing
                        : new ClubFeedEvent.AnnouncementAttachment(UUID.randomUUID().toString(), mediaId, now));
            }
            event.setAnnouncementAttachments(rebuilt);
        }

        if (mentionUserIds != null) {
            event.setMentionRefs(mentionResolver.resolve(
                    clubId, mentionUserIds, MentionResolver.CONTEXT_ANNOUNCEMENT, event.getId()));
        }

        feedEventRepository.save(event);

        ClubFeedEventResponse response = ClubFeedEventResponse.from(event, mediaResolver::resolve);
        broadcaster.broadcast(clubId, "feed_event_updated", response);
        return response;
    }

    /**
     * Delete an announcement. The author can always delete; admins/coaches may
     * also delete for moderation.
     */
    public void delete(String userId, String clubId, String feedEventId) {
        authorizationService.requireActiveMember(userId, clubId);

        ClubFeedEvent event = requireAnnouncement(clubId, feedEventId);

        boolean isAuthor = userId.equals(event.getAuthorId());
        boolean isAdmin = authorizationService.isAdminOrCoach(userId, clubId);
        if (!isAuthor && !isAdmin) {
            throw new IllegalStateException("Only the author or an admin can delete this announcement");
        }

        feedEventRepository.deleteById(feedEventId);
        broadcaster.broadcast(clubId, "feed_event_deleted", Map.of("feedEventId", feedEventId));
    }

    private ClubFeedEvent requireAnnouncement(String clubId, String feedEventId) {
        ClubFeedEvent event = feedEventRepository.findById(feedEventId)
                .filter(e -> clubId.equals(e.getClubId()))
                .orElseThrow(() -> new IllegalArgumentException("Feed event not found"));
        if (event.getType() != ClubFeedEventType.COACH_ANNOUNCEMENT) {
            throw new IllegalStateException("Event is not an announcement");
        }
        return event;
    }

    private void notifyMembers(String clubId, String authorId, User author, String content,
                               String eventId, List<ClubFeedEvent.MentionRef> mentionRefs) {
        Set<String> mentionedIds = mentionResolver.idsOf(mentionRefs);
        mentionedIds.remove(authorId);

        if (!mentionedIds.isEmpty()) {
            notificationService.sendToUsers(new ArrayList<>(mentionedIds),
                    "You were mentioned",
                    author.getDisplayName() + " mentioned you: " + truncate(content, NOTIFICATION_PREVIEW_CHARS),
                    Map.of("type", "CLUB_MENTION", "clubId", clubId, "feedEventId", eventId),
                    "clubMention");
        }

        // Copy the cached list before mutating: the cache returns the same instance
        // on every hit, so removing the author/mentioned ids in place would slowly
        // shrink the cached result.
        List<String> memberIds = new ArrayList<>(membershipService.getActiveMemberIds(clubId));
        memberIds.remove(authorId);
        memberIds.removeAll(mentionedIds);
        if (!memberIds.isEmpty()) {
            notificationService.sendToUsers(memberIds,
                    "Coach Announcement",
                    author.getDisplayName() + ": " + truncate(content, NOTIFICATION_PREVIEW_CHARS),
                    Map.of("type", "CLUB_ANNOUNCEMENT", "clubId", clubId),
                    "clubAnnouncement");
        }
    }

    private static String truncate(String s, int maxLen) {
        if (s == null) return "";
        return s.length() <= maxLen ? s : s.substring(0, maxLen) + "...";
    }
}
