package com.koval.trainingplannerbackend.club.feed;

import com.koval.trainingplannerbackend.auth.User;
import com.koval.trainingplannerbackend.auth.UserService;
import com.koval.trainingplannerbackend.integration.strava.StravaApiClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

/**
 * Off-thread fan-out of Strava kudos when a club member completes a session
 * that other members had previously bookmarked with "Give Kudos".
 *
 * <p>Extracted from {@link ClubFeedService} so the request thread completing the
 * session is not blocked on N sequential Strava API calls. Runs on the shared
 * bounded {@code taskExecutor}; saves the kudos-result audit entries when done.
 */
@Service
public class ClubAutoKudosService {

    private static final Logger log = LoggerFactory.getLogger(ClubAutoKudosService.class);

    private final ClubFeedEventRepository feedEventRepository;
    private final UserService userService;
    private final StravaApiClient stravaApiClient;

    public ClubAutoKudosService(ClubFeedEventRepository feedEventRepository,
                                UserService userService,
                                StravaApiClient stravaApiClient) {
        this.feedEventRepository = feedEventRepository;
        this.userService = userService;
        this.stravaApiClient = stravaApiClient;
    }

    /**
     * Re-load the feed event by id and post kudos on Strava on behalf of every user
     * who previously kudosed the event. Best-effort: per-user failures are recorded
     * but never re-raised.
     */
    @Async
    public void autoGiveKudos(String feedEventId, ClubFeedEvent.CompletionEntry newCompletion) {
        ClubFeedEvent event = feedEventRepository.findById(feedEventId).orElse(null);
        if (event == null) {
            log.warn("Feed event {} not found for auto-kudos", feedEventId);
            return;
        }
        if (newCompletion.stravaActivityId() == null || event.getKudosGivenBy().isEmpty()) {
            return;
        }

        for (String kudosGiverId : event.getKudosGivenBy()) {
            if (kudosGiverId.equals(newCompletion.userId())) continue;
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
}
