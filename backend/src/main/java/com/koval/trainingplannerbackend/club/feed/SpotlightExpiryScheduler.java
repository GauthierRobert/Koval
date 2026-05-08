package com.koval.trainingplannerbackend.club.feed;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * Hourly job that unpins MEMBER_SPOTLIGHT events whose {@code spotlightExpiresAt} is
 * in the past. Idempotent — running it twice has the same effect as running it once.
 */
@Component
public class SpotlightExpiryScheduler {

    private static final Logger log = LoggerFactory.getLogger(SpotlightExpiryScheduler.class);

    private final ClubFeedSpotlightService spotlightService;

    public SpotlightExpiryScheduler(ClubFeedSpotlightService spotlightService) {
        this.spotlightService = spotlightService;
    }

    @Scheduled(cron = "0 0 * * * *")
    public void unpinExpiredSpotlights() {
        int unpinned = spotlightService.unpinExpiredSpotlights(LocalDateTime.now());
        if (unpinned > 0) {
            log.info("Unpinned {} expired spotlight(s)", unpinned);
        }
    }
}
