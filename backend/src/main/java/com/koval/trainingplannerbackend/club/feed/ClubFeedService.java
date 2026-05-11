package com.koval.trainingplannerbackend.club.feed;

import com.koval.trainingplannerbackend.club.feed.dto.ClubFeedEventResponse;
import com.koval.trainingplannerbackend.club.feed.dto.ClubFeedResponse;
import com.koval.trainingplannerbackend.club.feed.dto.CompletionUpdatePayload;
import com.koval.trainingplannerbackend.club.membership.ClubAuthorizationService;
import com.koval.trainingplannerbackend.club.session.ClubTrainingSession;
import com.koval.trainingplannerbackend.club.session.ClubTrainingSessionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Read-side feed retrieval plus the session-completion roll-up event.
 *
 * <p>Other feed concerns are owned by dedicated services in this package:
 * announcements ({@link ClubAnnouncementService}), comments ({@link ClubFeedCommentService}),
 * photo enrichments ({@link ClubFeedPhotoService}), reactions
 * ({@link ClubFeedReactionService}), spotlights ({@link ClubFeedSpotlightService}).
 */
@Service
public class ClubFeedService {

    private static final Logger log = LoggerFactory.getLogger(ClubFeedService.class);

    private final ClubFeedEventRepository feedEventRepository;
    private final ClubTrainingSessionRepository clubSessionRepository;
    private final ClubAuthorizationService authorizationService;
    private final ClubFeedSseBroadcaster broadcaster;
    private final ClubAutoKudosService autoKudosService;
    private final ClubFeedMediaResolver mediaResolver;

    public ClubFeedService(ClubFeedEventRepository feedEventRepository,
                           ClubTrainingSessionRepository clubSessionRepository,
                           ClubAuthorizationService authorizationService,
                           ClubFeedSseBroadcaster broadcaster,
                           ClubAutoKudosService autoKudosService,
                           ClubFeedMediaResolver mediaResolver) {
        this.feedEventRepository = feedEventRepository;
        this.clubSessionRepository = clubSessionRepository;
        this.authorizationService = authorizationService;
        this.broadcaster = broadcaster;
        this.autoKudosService = autoKudosService;
        this.mediaResolver = mediaResolver;
    }

    /**
     * Get the paginated feed for a club: pinned events first, then chronological.
     */
    public ClubFeedResponse getFeed(String userId, String clubId, int page, int size) {
        authorizationService.requireActiveMember(userId, clubId);

        List<ClubFeedEventResponse> pinned = feedEventRepository
                .findByClubIdAndPinnedTrueOrderByCreatedAtDesc(clubId)
                .stream()
                .map(e -> ClubFeedEventResponse.from(e, mediaResolver::resolve))
                .toList();

        List<ClubFeedEventResponse> items = feedEventRepository
                .findByClubIdOrderByCreatedAtDesc(clubId, PageRequest.of(page, size))
                .stream()
                .filter(e -> !Boolean.TRUE.equals(e.getPinned())) // exclude pinned from timeline
                .map(e -> ClubFeedEventResponse.from(e, mediaResolver::resolve))
                .toList();

        boolean hasMore = items.size() == size;
        return new ClubFeedResponse(pinned, items, page, hasMore);
    }

    /**
     * Add a completion entry to the SESSION_COMPLETION feed event for a club session.
     * Creates the event if it doesn't exist, handles pinning/unpinning, and fires
     * auto-kudos on Strava (off-thread) for users who previously gave kudos on the event.
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

        if (entry.stravaActivityId() != null && !event.getKudosGivenBy().isEmpty()) {
            // Fire-and-forget: per-user Strava POSTs run on the bounded async pool so
            // the request thread (and the SSE broadcast below) is not delayed by
            // potentially-slow external HTTP calls.
            autoKudosService.autoGiveKudos(event.getId(), entry);
        }

        broadcaster.broadcast(clubSession.getClubId(), "completion_update",
                new CompletionUpdatePayload(
                        event.getId(),
                        clubSessionId,
                        event.getCompletions().size(),
                        new CompletionUpdatePayload.LatestCompletion(
                                entry.userId(), entry.displayName(), entry.profilePicture())));
    }

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
}
