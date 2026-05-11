package com.koval.trainingplannerbackend.club.feed;

import com.koval.trainingplannerbackend.auth.User;
import com.koval.trainingplannerbackend.auth.UserService;
import com.koval.trainingplannerbackend.club.feed.dto.KudosResponse;
import com.koval.trainingplannerbackend.integration.strava.StravaApiClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

@Service
public class StravaKudosService {

    private static final Logger log = LoggerFactory.getLogger(StravaKudosService.class);

    private final ClubFeedEventRepository feedEventRepository;
    private final UserService userService;
    private final StravaApiClient stravaApiClient;
    private final ClubFeedSseBroadcaster broadcaster;

    public StravaKudosService(ClubFeedEventRepository feedEventRepository,
                              UserService userService,
                              StravaApiClient stravaApiClient,
                              ClubFeedSseBroadcaster broadcaster) {
        this.feedEventRepository = feedEventRepository;
        this.userService = userService;
        this.stravaApiClient = stravaApiClient;
        this.broadcaster = broadcaster;
    }

    /**
     * Give Strava kudos from the requesting user to all athletes in a feed event
     * who have a linked Strava activity.
     */
    public KudosResponse giveKudosToAll(String requestingUserId, String feedEventId) {
        User requestingUser = userService.findById(requestingUserId)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));

        if (requestingUser.getStravaRefreshToken() == null || requestingUser.getStravaRefreshToken().isBlank()) {
            throw new IllegalStateException("Strava is not connected for this user");
        }

        ClubFeedEvent event = feedEventRepository.findById(feedEventId)
                .orElseThrow(() -> new IllegalArgumentException("Feed event not found"));

        if (event.getKudosGivenBy().contains(requestingUserId)) {
            throw new IllegalStateException("Kudos already given by this user");
        }

        List<ClubFeedEvent.CompletionEntry> targets = event.getCompletions().stream()
                .filter(c -> c.stravaActivityId() != null)
                .filter(c -> !c.userId().equals(requestingUserId))
                .toList();

        // Each Strava POST is independent network I/O — fan out so total latency
        // is dominated by the slowest call rather than the sum of all calls.
        List<CompletableFuture<KudosCallOutcome>> futures = targets.stream()
                .map(c -> CompletableFuture.supplyAsync(() -> postOneKudos(requestingUser, c)))
                .toList();

        List<KudosResponse.KudosResultDto> results = new ArrayList<>();
        int successCount = 0;
        int failCount = 0;
        LocalDateTime now = LocalDateTime.now();

        for (CompletableFuture<KudosCallOutcome> f : futures) {
            KudosCallOutcome o = f.join();
            results.add(new KudosResponse.KudosResultDto(
                    o.completion.displayName(), o.completion.stravaActivityId(), o.success, o.errorMessage));
            event.getKudosResults().add(new ClubFeedEvent.KudosResult(
                    o.completion.userId(), o.completion.stravaActivityId(),
                    requestingUserId, o.success, o.errorMessage, now));
            if (o.success) successCount++; else failCount++;
        }

        event.getKudosGivenBy().add(requestingUserId);
        feedEventRepository.save(event);

        // Broadcast kudos update
        broadcaster.broadcast(event.getClubId(), "kudos_update",
                new KudosUpdatePayload(event.getId(), requestingUserId, successCount));

        return new KudosResponse(results, successCount, failCount);
    }

    private KudosCallOutcome postOneKudos(User requestingUser, ClubFeedEvent.CompletionEntry completion) {
        try {
            stravaApiClient.giveKudos(requestingUser, completion.stravaActivityId());
            return new KudosCallOutcome(completion, true, null);
        } catch (RuntimeException e) {
            log.warn("Failed to give kudos to activity {}: {}", completion.stravaActivityId(), e.getMessage());
            return new KudosCallOutcome(completion, false, e.getMessage());
        }
    }

    private record KudosCallOutcome(ClubFeedEvent.CompletionEntry completion, boolean success, String errorMessage) {}

    private record KudosUpdatePayload(String feedEventId, String givenByUserId, int successCount) {}
}
