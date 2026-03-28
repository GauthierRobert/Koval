package com.koval.trainingplannerbackend.club.feed;

import com.koval.trainingplannerbackend.auth.User;
import com.koval.trainingplannerbackend.auth.UserService;
import com.koval.trainingplannerbackend.club.feed.dto.ClubFeedEventResponse;
import com.koval.trainingplannerbackend.club.feed.dto.ClubFeedResponse;
import com.koval.trainingplannerbackend.club.feed.dto.CompletionUpdatePayload;
import com.koval.trainingplannerbackend.club.membership.ClubAuthorizationService;
import com.koval.trainingplannerbackend.club.membership.ClubMemberStatus;
import com.koval.trainingplannerbackend.club.membership.ClubMembership;
import com.koval.trainingplannerbackend.club.membership.ClubMembershipRepository;
import com.koval.trainingplannerbackend.club.session.ClubTrainingSession;
import com.koval.trainingplannerbackend.club.session.ClubTrainingSessionRepository;
import com.koval.trainingplannerbackend.notification.NotificationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
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

    public ClubFeedService(ClubFeedEventRepository feedEventRepository,
                           ClubTrainingSessionRepository clubSessionRepository,
                           ClubAuthorizationService authorizationService,
                           ClubMembershipRepository membershipRepository,
                           UserService userService,
                           NotificationService notificationService,
                           ClubFeedSseBroadcaster broadcaster) {
        this.feedEventRepository = feedEventRepository;
        this.clubSessionRepository = clubSessionRepository;
        this.authorizationService = authorizationService;
        this.membershipRepository = membershipRepository;
        this.userService = userService;
        this.notificationService = notificationService;
        this.broadcaster = broadcaster;
    }

    /**
     * Get the paginated feed for a club: pinned events first, then chronological.
     */
    public ClubFeedResponse getFeed(String userId, String clubId, int page, int size) {
        authorizationService.requireActiveMember(userId, clubId);

        List<ClubFeedEventResponse> pinned = feedEventRepository
                .findByClubIdAndPinnedTrueOrderByCreatedAtDesc(clubId)
                .stream()
                .map(ClubFeedEventResponse::from)
                .toList();

        List<ClubFeedEventResponse> items = feedEventRepository
                .findByClubIdOrderByCreatedAtDesc(clubId, PageRequest.of(page, size))
                .stream()
                .filter(e -> !e.isPinned()) // exclude pinned from timeline
                .map(ClubFeedEventResponse::from)
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
        if (!event.isPinned()) {
            unpinPrevious(clubSession.getClubId(), ClubFeedEventType.SESSION_COMPLETION);
            event.setPinned(true);
        }

        feedEventRepository.save(event);

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
     * Create a coach announcement in the feed.
     */
    public ClubFeedEventResponse createCoachAnnouncement(String userId, String clubId, String content) {
        authorizationService.requireAdminOrCoach(userId, clubId);

        User author = userService.findById(userId).orElseThrow();

        ClubFeedEvent event = new ClubFeedEvent();
        event.setClubId(clubId);
        event.setType(ClubFeedEventType.COACH_ANNOUNCEMENT);
        event.setAuthorId(userId);
        event.setAuthorName(author.getDisplayName());
        event.setAuthorProfilePicture(author.getProfilePicture());
        event.setAnnouncementContent(content);
        event.setCreatedAt(LocalDateTime.now());
        event.setUpdatedAt(LocalDateTime.now());
        feedEventRepository.save(event);

        // Broadcast via SSE
        ClubFeedEventResponse response = ClubFeedEventResponse.from(event);
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
