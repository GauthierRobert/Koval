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

        List<KudosResponse.KudosResultDto> results = new ArrayList<>();
        int successCount = 0;
        int failCount = 0;

        for (ClubFeedEvent.CompletionEntry completion : event.getCompletions()) {
            if (completion.stravaActivityId() == null) continue;
            if (completion.userId().equals(requestingUserId)) continue; // skip self

            try {
                stravaApiClient.giveKudos(requestingUser, completion.stravaActivityId());
                results.add(new KudosResponse.KudosResultDto(
                        completion.displayName(), completion.stravaActivityId(), true, null));
                event.getKudosResults().add(new ClubFeedEvent.KudosResult(
                        completion.userId(), completion.stravaActivityId(),
                        requestingUserId, true, null, LocalDateTime.now()));
                successCount++;
            } catch (Exception e) {
                log.warn("Failed to give kudos to activity {}: {}", completion.stravaActivityId(), e.getMessage());
                results.add(new KudosResponse.KudosResultDto(
                        completion.displayName(), completion.stravaActivityId(), false, e.getMessage()));
                event.getKudosResults().add(new ClubFeedEvent.KudosResult(
                        completion.userId(), completion.stravaActivityId(),
                        requestingUserId, false, e.getMessage(), LocalDateTime.now()));
                failCount++;
            }
        }

        event.getKudosGivenBy().add(requestingUserId);
        feedEventRepository.save(event);

        // Broadcast kudos update
        broadcaster.broadcast(event.getClubId(), "kudos_update",
                new KudosUpdatePayload(event.getId(), requestingUserId, successCount));

        return new KudosResponse(results, successCount, failCount);
    }

    private record KudosUpdatePayload(String feedEventId, String givenByUserId, int successCount) {}
}
