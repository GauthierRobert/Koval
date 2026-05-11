package com.koval.trainingplannerbackend.race.briefing;

import com.koval.trainingplannerbackend.auth.SecurityUtils;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;
import java.util.NoSuchElementException;

/**
 * REST endpoint for the race-day briefing pack.
 *
 * <p>Public per-race endpoint with athlete personalization layered on top:
 * the zone-target section is populated from the authenticated user's default
 * zone systems. Anonymous callers still get the course/weather/gear sections.
 */
@RestController
@RequestMapping("/api/races")
public class RaceBriefingController {

    private final RaceBriefingService briefingService;

    public RaceBriefingController(RaceBriefingService briefingService) {
        this.briefingService = briefingService;
    }

    @GetMapping("/{id}/briefing")
    public ResponseEntity<RaceBriefingResponse> getBriefing(@PathVariable String id) {
        String userId;
        try {
            userId = SecurityUtils.getCurrentUserId();
        } catch (RuntimeException e) {
            userId = null;
        }
        try {
            RaceBriefingResponse briefing = briefingService.generate(id, userId);
            // Briefings change with weather updates (Open-Meteo refreshes hourly),
            // so cache for 30 minutes — long enough to deduplicate refreshes,
            // short enough that storms don't surprise anyone.
            return ResponseEntity.ok()
                    .cacheControl(CacheControl.maxAge(Duration.ofMinutes(30)).cachePrivate())
                    .body(briefing);
        } catch (NoSuchElementException e) {
            return ResponseEntity.notFound().build();
        }
    }
}
