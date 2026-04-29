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
import java.util.List;
import java.util.Map;
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

    public ClubFeedService(ClubFeedEventRepository feedEventRepository,
                           ClubTrainingSessionRepository clubSessionRepository,
                           ClubAuthorizationService authorizationService,
                           ClubMembershipRepository membershipRepository,
                           UserService userService,
                           NotificationService notificationService,
                           ClubFeedSseBroadcaster broadcaster,
                           StravaApiClient stravaApiClient,
                           MediaService mediaService) {
        this.feedEventRepository = feedEventRepository;
        this.clubSessionRepository = clubSessionRepository;
        this.authorizationService = authorizationService;
        this.membershipRepository = membershipRepository;
        this.userService = userService;
        this.notificationService = notificationService;
        this.broadcaster = broadcaster;
        this.stravaApiClient = stravaApiClient;
        this.mediaService = mediaService;
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
        feedEventRepository.save(event);

        // Broadcast via SSE
        ClubFeedEventResponse response = ClubFeedEventResponse.from(event, this::resolveMedia);
        broadcaster.broadcast(clubId, "new_feed_event", response);

        // Send push notification to all club members
        List<String> memberIds = getActiveMemberIds(clubId);
        memberIds.remove(userId);
        if (!memberIds.isEmpty()) {
            notificationService.sendToUsers(memberIds,
                    "Coach Announcement",
                    author.getDisplayName() + ": " + truncate(content, 100),
                    Map.of("type", "CLUB_ANNOUNCEMENT", "clubId", clubId),
                    "clubAnnouncement");
        }

        return response;
    }

    /**
     * Add a comment to a feed event.
     */
    public ClubFeedEvent.CommentEntry addComment(String userId, String clubId, String feedEventId, String content) {
        authorizationService.requireActiveMember(userId, clubId);

        ClubFeedEvent event = feedEventRepository.findById(feedEventId)
                .filter(e -> clubId.equals(e.getClubId()))
                .orElseThrow(() -> new IllegalArgumentException("Feed event not found"));

        User author = userService.findById(userId).orElseThrow();

        ClubFeedEvent.CommentEntry comment = new ClubFeedEvent.CommentEntry(
                UUID.randomUUID().toString(),
                userId,
                author.getDisplayName(),
                author.getProfilePicture(),
                content,
                LocalDateTime.now());

        event.getComments().add(comment);
        event.setUpdatedAt(LocalDateTime.now());
        feedEventRepository.save(event);

        broadcaster.broadcast(clubId, "comment_update",
                new CommentUpdatePayload(feedEventId, comment));

        return comment;
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
