package com.koval.trainingplannerbackend.club.feed;

import com.koval.trainingplannerbackend.auth.User;
import com.koval.trainingplannerbackend.auth.UserService;
import com.koval.trainingplannerbackend.training.history.CompletedSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

@Component
public class ClubSessionCompletionListener {

    private static final Logger log = LoggerFactory.getLogger(ClubSessionCompletionListener.class);

    private final ClubFeedService feedService;
    private final UserService userService;

    public ClubSessionCompletionListener(ClubFeedService feedService, UserService userService) {
        this.feedService = feedService;
        this.userService = userService;
    }

    @EventListener
    @Async
    public void onSessionCompleted(SessionCompletedEvent event) {
        CompletedSession session = event.session();
        if (session.getClubSessionId() == null) return;

        try {
            User user = userService.findById(session.getUserId()).orElse(null);
            String displayName = user != null ? user.getDisplayName() : "Unknown";
            String profilePicture = user != null ? user.getProfilePicture() : null;

            ClubFeedEvent.CompletionEntry entry = new ClubFeedEvent.CompletionEntry(
                    session.getUserId(),
                    displayName,
                    profilePicture,
                    session.getId(),
                    session.getStravaActivityId(),
                    session.getCompletedAt());

            feedService.addCompletionToEvent(session.getClubSessionId(), entry);
        } catch (Exception e) {
            log.error("Failed to process session completion for feed: {}", e.getMessage(), e);
        }
    }
}
