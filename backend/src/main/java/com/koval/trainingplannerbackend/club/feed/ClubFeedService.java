package com.koval.trainingplannerbackend.club.feed;

import com.koval.trainingplannerbackend.auth.User;
import com.koval.trainingplannerbackend.auth.UserService;
import com.koval.trainingplannerbackend.club.feed.dto.ClubFeedEventResponse;
import com.koval.trainingplannerbackend.club.feed.dto.ClubFeedResponse;
import com.koval.trainingplannerbackend.club.feed.dto.CompletionUpdatePayload;
import com.koval.trainingplannerbackend.club.feed.dto.PhotoEnrichmentResponse;
import com.koval.trainingplannerbackend.club.membership.ClubAuthorizationService;
import com.koval.trainingplannerbackend.club.membership.ClubMemberStatus;
import com.koval.trainingplannerbackend.club.membership.ClubMembership;
import com.koval.trainingplannerbackend.club.membership.ClubMembershipRepository;
import com.koval.trainingplannerbackend.club.session.ClubTrainingSession;
import com.koval.trainingplannerbackend.club.session.ClubTrainingSessionRepository;
import com.koval.trainingplannerbackend.integration.strava.StravaApiClient;
import com.koval.trainingplannerbackend.media.Media;
import com.koval.trainingplannerbackend.media.MediaPurpose;
import com.koval.trainingplannerbackend.media.MediaService;
import com.koval.trainingplannerbackend.media.dto.MediaResponse;
import com.koval.trainingplannerbackend.notification.NotificationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import com.koval.trainingplannerbackend.club.feed.dto.CommentUpdatePayload;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class ClubFeedService {

    private static final Logger log = LoggerFactory.getLogger(ClubFeedService.class);

    private final ClubFeedEventRepository feedEventRepository;
    private final ClubTrainingSessionRepository clubSessionRepository;
    private final ClubAuthorizationService authorizationService;
    private final ClubMembershipRepository membershipRepository;
    private final UserService userService;
    private final NotificationService notificationService;
    private final ClubFeedSseBroadcaster broadcaster;
    private final StravaApiClient stravaApiClient;
    private final MediaService mediaService;
    private final MentionResolver mentionResolver;

    public ClubFeedService(ClubFeedEventRepository feedEventRepository,
                           ClubTrainingSessionRepository clubSessionRepository,
                           ClubAuthorizationService authorizationService,
                           ClubMembershipRepository membershipRepository,
                           UserService userService,
                           NotificationService notificationService,
                           ClubFeedSseBroadcaster broadcaster,
                           StravaApiClient stravaApiClient,
                           MediaService mediaService,
                           MentionResolver mentionResolver) {
        this.feedEventRepository = feedEventRepository;
        this.clubSessionRepository = clubSessionRepository;
        this.authorizationService = authorizationService;
        this.membershipRepository = membershipRepository;
        this.userService = userService;
        this.notificationService = notificationService;
        this.broadcaster = broadcaster;
        this.stravaApiClient = stravaApiClient;
        this.mediaService = mediaService;
        this.mentionResolver = mentionResolver;
    }

    /** Resolve a mediaId to its full read DTO, or null if not found / not confirmed. */
    private MediaResponse resolveMedia(String mediaId) {
        return mediaService.findById(mediaId)
                .filter(Media::isConfirmed)
                .map(mediaService::buildMediaResponse)
                .orElse(null);
    }

    /**
     * Get the paginated feed for a club: pinned events first, then chronological.
     */
    public ClubFeedResponse getFeed(String userId, String clubId, int page, int size) {
        authorizationService.requireActiveMember(userId, clubId);

        List<ClubFeedEventResponse> pinned = feedEventRepository
                .findByClubIdAndPinnedTrueOrderByCreatedAtDesc(clubId)
                .stream()
                .map(e -> ClubFeedEventResponse.from(e, this::resolveMedia))
                .toList();

        List<ClubFeedEventResponse> items = feedEventRepository
                .findByClubIdOrderByCreatedAtDesc(clubId, PageRequest.of(page, size))
                .stream()
                .filter(e -> !Boolean.TRUE.equals(e.getPinned())) // exclude pinned from timeline
                .map(e -> ClubFeedEventResponse.from(e, this::resolveMedia))
                .toList();

        boolean hasMore = items.size() == size;
        return new ClubFeedResponse(pinned, items, page, hasMore);
    }

    /**
     * Add a completion entry to the SESSION_COMPLETION feed event for a club session.
     * Creates the event if it doesn't exist, handles pinning/unpinning.
     */
    public void addCompletionToEvent(String clubSessionId, ClubFeedEvent.CompletionEntry entry) {
        ClubTrainingSession clubSession = clubSessionRepository.findById(clubSessionId).orElse(null);
        if (clubSession == null) {
            log.warn("Club session {} not found for completion event", clubSessionId);
            return;
        }

        ClubFeedEvent event = feedEventRepository
                .findByClubSessionIdAndType(clubSessionId, ClubFeedEventType.SESSION_COMPLETION)
                .orElseGet(() -> createSessionCompletionEvent(clubSession));

        // Deduplicate by userId
        boolean alreadyPresent = event.getCompletions().stream()
                .anyMatch(c -> c.userId().equals(entry.userId()));
        if (alreadyPresent) return;

        event.getCompletions().add(entry);
        event.setUpdatedAt(LocalDateTime.now());

        // Pin this event and unpin previous
        if (!Boolean.TRUE.equals(event.getPinned())) {
            unpinPrevious(clubSession.getClubId(), ClubFeedEventType.SESSION_COMPLETION);
            event.setPinned(true);
        }

        feedEventRepository.save(event);

        // Auto-kudos: if someone already gave kudos, give kudos from them to this new completion
        if (entry.stravaActivityId() != null && !event.getKudosGivenBy().isEmpty()) {
            autoGiveKudos(event, entry);
        }

        // Broadcast live update via SSE
        broadcaster.broadcast(clubSession.getClubId(), "completion_update",
                new CompletionUpdatePayload(
                        event.getId(),
                        clubSessionId,
                        event.getCompletions().size(),
                        new CompletionUpdatePayload.LatestCompletion(
                                entry.userId(), entry.displayName(), entry.profilePicture())));
    }

    /**
     * Automatically give Strava kudos from all users who previously clicked "Give Kudos"
     * to a newly added completion.
     */
    private void autoGiveKudos(ClubFeedEvent event, ClubFeedEvent.CompletionEntry newCompletion) {
        for (String kudosGiverId : event.getKudosGivenBy()) {
            if (kudosGiverId.equals(newCompletion.userId())) continue; // skip self
            try {
                User giver = userService.findById(kudosGiverId).orElse(null);
                if (giver == null || giver.getStravaRefreshToken() == null) continue;
                stravaApiClient.giveKudos(giver, newCompletion.stravaActivityId());
                event.getKudosResults().add(new ClubFeedEvent.KudosResult(
                        newCompletion.userId(), newCompletion.stravaActivityId(),
                        kudosGiverId, true, null, LocalDateTime.now()));
            } catch (Exception e) {
                log.warn("Auto-kudos failed from {} to activity {}: {}",
                        kudosGiverId, newCompletion.stravaActivityId(), e.getMessage());
                event.getKudosResults().add(new ClubFeedEvent.KudosResult(
                        newCompletion.userId(), newCompletion.stravaActivityId(),
                        kudosGiverId, false, e.getMessage(), LocalDateTime.now()));
            }
        }
        feedEventRepository.save(event);
    }

    /**
     * Create a coach announcement in the feed.
     *
     * Optional {@code mediaIds} are attachments (images, PDFs, docs) the coach
     * uploaded with purpose {@code ANNOUNCEMENT_ATTACHMENT} prior to posting.
     */
    public ClubFeedEventResponse createCoachAnnouncement(String userId, String clubId, String content,
                                                         List<String> mediaIds) {
        return createCoachAnnouncement(userId, clubId, content, mediaIds, List.of());
    }

    /**
     * Create a coach announcement with optional @mentions.
     * Mentioned users are notified with a high-priority {@code clubMention} channel
     * (deduped against the broadcast {@code clubAnnouncement} so they don't receive both).
     */
    public ClubFeedEventResponse createCoachAnnouncement(String userId, String clubId, String content,
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

        // Resolve and persist mentions before saving so the event id is available as context.
        feedEventRepository.save(event);
        List<ClubFeedEvent.MentionRef> refs = mentionResolver.resolve(
                clubId, mentionUserIds, MentionResolver.CONTEXT_ANNOUNCEMENT, event.getId());
        if (!refs.isEmpty()) {
            event.setMentionRefs(refs);
            feedEventRepository.save(event);
        }

        ClubFeedEventResponse response = ClubFeedEventResponse.from(event, this::resolveMedia);
        broadcaster.broadcast(clubId, "new_feed_event", response);

        Set<String> mentionedIds = mentionResolver.idsOf(refs);
        mentionedIds.remove(userId);

        // Push notification to mentioned users (high-priority channel)
        if (!mentionedIds.isEmpty()) {
            notificationService.sendToUsers(new ArrayList<>(mentionedIds),
                    "You were mentioned",
                    author.getDisplayName() + " mentioned you: " + truncate(content, 100),
                    Map.of("type", "CLUB_MENTION", "clubId", clubId, "feedEventId", event.getId()),
                    "clubMention");
        }

        // Broadcast push notification to remaining active members (excluding author + mentioned)
        List<String> memberIds = getActiveMemberIds(clubId);
        memberIds.remove(userId);
        memberIds.removeAll(mentionedIds);
        if (!memberIds.isEmpty()) {
            notificationService.sendToUsers(memberIds,
                    "Coach Announcement",
                    author.getDisplayName() + ": " + truncate(content, 100),
                    Map.of("type", "CLUB_ANNOUNCEMENT", "clubId", clubId),
                    "clubAnnouncement");
        }

        return response;
    }

    /** Add a top-level comment to a feed event (no parent, no mentions). */
    public ClubFeedEvent.CommentEntry addComment(String userId, String clubId, String feedEventId, String content) {
        return addComment(userId, clubId, feedEventId, content, null, List.of());
    }

    /**
     * Add a comment or reply to a feed event with optional mentions.
     * Pass {@code parentCommentId} to make this a reply; replies must point at a
     * top-level comment (single-level enforcement).
     */
    public ClubFeedEvent.CommentEntry addComment(String userId, String clubId, String feedEventId,
                                                 String content, String parentCommentId,
                                                 List<String> mentionUserIds) {
        authorizationService.requireActiveMember(userId, clubId);

        ClubFeedEvent event = feedEventRepository.findById(feedEventId)
                .filter(e -> clubId.equals(e.getClubId()))
                .orElseThrow(() -> new IllegalArgumentException("Feed event not found"));

        ClubFeedEvent.CommentEntry parent = null;
        if (parentCommentId != null) {
            parent = event.getComments().stream()
                    .filter(c -> c.id().equals(parentCommentId))
                    .findFirst()
                    .orElseThrow(() -> new IllegalArgumentException("Parent comment not found"));
            if (parent.parentCommentId() != null) {
                throw new IllegalStateException("Replies are limited to one level deep");
            }
        }

        User author = userService.findById(userId).orElseThrow();
        String commentId = UUID.randomUUID().toString();

        List<ClubFeedEvent.MentionRef> mentions = mentionResolver.resolve(
                clubId, mentionUserIds,
                parentCommentId == null ? MentionResolver.CONTEXT_COMMENT : MentionResolver.CONTEXT_REPLY,
                commentId);

        ClubFeedEvent.CommentEntry comment = new ClubFeedEvent.CommentEntry(
                commentId,
                userId,
                author.getDisplayName(),
                author.getProfilePicture(),
                content,
                LocalDateTime.now(),
                null,
                parentCommentId,
                new HashMap<>(),
                mentions);

        event.getComments().add(comment);
        event.setUpdatedAt(LocalDateTime.now());
        feedEventRepository.save(event);

        String broadcastEvent = parentCommentId == null ? "comment_update" : "comment_reply_added";
        broadcaster.broadcast(clubId, broadcastEvent,
                new CommentUpdatePayload(feedEventId, comment));

        // Notifications: parent author (for replies) + mentioned users.
        Set<String> notified = new HashSet<>();
        if (parent != null && !parent.userId().equals(userId)) {
            notificationService.sendToUsers(List.of(parent.userId()),
                    "New reply",
                    author.getDisplayName() + " replied: " + truncate(content, 100),
                    Map.of("type", "CLUB_REPLY", "clubId", clubId, "feedEventId", feedEventId,
                            "commentId", commentId),
                    "clubReply");
            notified.add(parent.userId());
        }
        Set<String> mentionedIds = mentionResolver.idsOf(mentions);
        mentionedIds.remove(userId);
        mentionedIds.removeAll(notified);
        if (!mentionedIds.isEmpty()) {
            notificationService.sendToUsers(new ArrayList<>(mentionedIds),
                    "You were mentioned",
                    author.getDisplayName() + " mentioned you: " + truncate(content, 100),
                    Map.of("type", "CLUB_MENTION", "clubId", clubId, "feedEventId", feedEventId,
                            "commentId", commentId),
                    "clubMention");
        }

        return comment;
    }

    /**
     * Edit an existing coach announcement. Only the original author may edit.
     * Replaces content and (if provided) the full attachment list.
     */
    public ClubFeedEventResponse updateCoachAnnouncement(String userId, String clubId, String feedEventId,
                                                         String content, List<String> mediaIds) {
        return updateCoachAnnouncement(userId, clubId, feedEventId, content, mediaIds, List.of());
    }

    public ClubFeedEventResponse updateCoachAnnouncement(String userId, String clubId, String feedEventId,
                                                         String content, List<String> mediaIds,
                                                         List<String> mentionUserIds) {
        authorizationService.requireActiveMember(userId, clubId);

        ClubFeedEvent event = feedEventRepository.findById(feedEventId)
                .filter(e -> clubId.equals(e.getClubId()))
                .orElseThrow(() -> new IllegalArgumentException("Feed event not found"));

        if (event.getType() != ClubFeedEventType.COACH_ANNOUNCEMENT) {
            throw new IllegalStateException("Event is not an announcement");
        }
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
            // Rebuild attachments list — preserve enrichment ids for media kept; create new ones for added.
            List<ClubFeedEvent.AnnouncementAttachment> rebuilt = new java.util.ArrayList<>();
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

        ClubFeedEventResponse response = ClubFeedEventResponse.from(event, this::resolveMedia);
        broadcaster.broadcast(clubId, "feed_event_updated", response);
        return response;
    }

    /**
     * Delete a coach announcement. Author may delete; admins/owners/coaches may
     * also delete for moderation.
     */
    public void deleteCoachAnnouncement(String userId, String clubId, String feedEventId) {
        authorizationService.requireActiveMember(userId, clubId);

        ClubFeedEvent event = feedEventRepository.findById(feedEventId)
                .filter(e -> clubId.equals(e.getClubId()))
                .orElseThrow(() -> new IllegalArgumentException("Feed event not found"));

        if (event.getType() != ClubFeedEventType.COACH_ANNOUNCEMENT) {
            throw new IllegalStateException("Event is not an announcement");
        }

        boolean isAuthor = userId.equals(event.getAuthorId());
        boolean isAdmin = authorizationService.isAdminOrCoach(userId, clubId);
        if (!isAuthor && !isAdmin) {
            throw new IllegalStateException("Only the author or an admin can delete this announcement");
        }

        feedEventRepository.deleteById(feedEventId);

        java.util.Map<String, Object> payload = new java.util.HashMap<>();
        payload.put("feedEventId", feedEventId);
        broadcaster.broadcast(clubId, "feed_event_deleted", payload);
    }

    /**
     * Edit a comment. Only the original author may edit.
     */
    public ClubFeedEvent.CommentEntry updateComment(String userId, String clubId, String feedEventId,
                                                    String commentId, String content) {
        authorizationService.requireActiveMember(userId, clubId);

        ClubFeedEvent event = feedEventRepository.findById(feedEventId)
                .filter(e -> clubId.equals(e.getClubId()))
                .orElseThrow(() -> new IllegalArgumentException("Feed event not found"));

        ClubFeedEvent.CommentEntry existing = event.getComments().stream()
                .filter(c -> c.id().equals(commentId))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Comment not found"));

        if (!userId.equals(existing.userId())) {
            throw new IllegalStateException("Only the author can edit this comment");
        }

        LocalDateTime now = LocalDateTime.now();
        ClubFeedEvent.CommentEntry updated = new ClubFeedEvent.CommentEntry(
                existing.id(), existing.userId(), existing.displayName(), existing.profilePicture(),
                content, existing.createdAt(), now,
                existing.parentCommentId(),
                existing.reactions() != null ? existing.reactions() : new HashMap<>(),
                existing.mentions() != null ? existing.mentions() : List.of());

        int idx = event.getComments().indexOf(existing);
        event.getComments().set(idx, updated);
        event.setUpdatedAt(now);
        feedEventRepository.save(event);

        broadcaster.broadcast(clubId, "comment_edited",
                new CommentUpdatePayload(feedEventId, updated));

        return updated;
    }

    /**
     * Delete a comment. Author may delete; admins/coaches/owners may also delete
     * for moderation.
     */
    public void deleteComment(String userId, String clubId, String feedEventId, String commentId) {
        authorizationService.requireActiveMember(userId, clubId);

        ClubFeedEvent event = feedEventRepository.findById(feedEventId)
                .filter(e -> clubId.equals(e.getClubId()))
                .orElseThrow(() -> new IllegalArgumentException("Feed event not found"));

        ClubFeedEvent.CommentEntry existing = event.getComments().stream()
                .filter(c -> c.id().equals(commentId))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Comment not found"));

        boolean isAuthor = userId.equals(existing.userId());
        boolean isAdmin = authorizationService.isAdminOrCoach(userId, clubId);
        if (!isAuthor && !isAdmin) {
            throw new IllegalStateException("Only the author or an admin can delete this comment");
        }

        // If deleting a top-level comment, cascade-remove its replies.
        boolean isTopLevel = existing.parentCommentId() == null;
        event.getComments().removeIf(c -> c.id().equals(commentId)
                || (isTopLevel && commentId.equals(c.parentCommentId())));
        event.setUpdatedAt(LocalDateTime.now());
        feedEventRepository.save(event);

        java.util.Map<String, Object> payload = new java.util.HashMap<>();
        payload.put("feedEventId", feedEventId);
        payload.put("commentId", commentId);
        broadcaster.broadcast(clubId, "comment_deleted", payload);
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
        ClubFeedEvent event = feedEventRepository.findById(feedEventId)
                .filter(e -> clubId.equals(e.getClubId()))
                .orElseThrow(() -> new IllegalArgumentException("Feed event not found"));

        mediaService.requireOwnedAndConfirmed(userId, mediaIds, MediaPurpose.FEED_POST_ENRICHMENT);

        User author = userService.findById(userId).orElseThrow();
        List<PhotoEnrichmentResponse> added = new java.util.ArrayList<>();
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
            added.add(PhotoEnrichmentResponse.from(enrichment, this::resolveMedia));
        }
        event.setUpdatedAt(now);
        feedEventRepository.save(event);

        java.util.Map<String, Object> payload = new java.util.HashMap<>();
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
        ClubFeedEvent event = feedEventRepository.findById(feedEventId)
                .filter(e -> clubId.equals(e.getClubId()))
                .orElseThrow(() -> new IllegalArgumentException("Feed event not found"));

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

        java.util.Map<String, Object> payload = new java.util.HashMap<>();
        payload.put("feedEventId", feedEventId);
        payload.put("enrichmentId", enrichmentId);
        broadcaster.broadcast(clubId, "feed_photo_removed", payload);
    }

    // --- Private helpers ---

    private ClubFeedEvent createSessionCompletionEvent(ClubTrainingSession clubSession) {
        ClubFeedEvent event = new ClubFeedEvent();
        event.setClubId(clubSession.getClubId());
        event.setType(ClubFeedEventType.SESSION_COMPLETION);
        event.setClubSessionId(clubSession.getId());
        event.setSessionTitle(clubSession.getTitle());
        event.setSessionSport(clubSession.getSport());
        event.setSessionScheduledAt(clubSession.getScheduledAt());
        event.setCreatedAt(LocalDateTime.now());
        event.setUpdatedAt(LocalDateTime.now());
        return event;
    }

    private void unpinPrevious(String clubId, ClubFeedEventType type) {
        feedEventRepository.findFirstByClubIdAndTypeAndPinnedTrueOrderByCreatedAtDesc(clubId, type)
                .ifPresent(prev -> {
                    prev.setPinned(false);
                    feedEventRepository.save(prev);
                });
    }

    private List<String> getActiveMemberIds(String clubId) {
        return membershipRepository.findByClubIdAndStatus(clubId, ClubMemberStatus.ACTIVE)
                .stream()
                .map(ClubMembership::getUserId)
                .collect(Collectors.toList());
    }

    private static String truncate(String s, int maxLen) {
        if (s == null) return "";
        return s.length() <= maxLen ? s : s.substring(0, maxLen) + "...";
    }
}
