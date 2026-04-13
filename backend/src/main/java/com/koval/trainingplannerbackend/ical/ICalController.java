package com.koval.trainingplannerbackend.ical;

import com.koval.trainingplannerbackend.auth.User;
import com.koval.trainingplannerbackend.auth.UserService;
import com.koval.trainingplannerbackend.config.exceptions.ResourceNotFoundException;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;

/** Public (no JWT) endpoint that serves an iCalendar feed secured by a per-user feed token. */
@RestController
@RequestMapping("/api/ical")
public class ICalController {

    private final ICalService iCalService;
    private final UserService userService;

    public ICalController(ICalService iCalService, UserService userService) {
        this.iCalService = iCalService;
        this.userService = userService;
    }

    @GetMapping(value = "/{feedToken}", produces = "text/calendar")
    public ResponseEntity<String> getCalendarFeed(@PathVariable String feedToken) {
        User user = userService.findByCalendarFeedToken(feedToken)
                .orElseThrow(() -> new ResourceNotFoundException("Calendar feed", feedToken));

        String icsContent = iCalService.generateFeed(user);

        return ResponseEntity.ok()
                .header("Content-Disposition", "attachment; filename=\"training-schedule.ics\"")
                .cacheControl(CacheControl.maxAge(Duration.ofMinutes(15)))
                .body(icsContent);
    }
}
