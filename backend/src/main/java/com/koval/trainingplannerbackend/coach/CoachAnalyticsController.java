package com.koval.trainingplannerbackend.coach;

import com.koval.trainingplannerbackend.auth.SecurityUtils;
import com.koval.trainingplannerbackend.goal.RaceGoal;
import com.koval.trainingplannerbackend.goal.RaceGoalResponse;
import com.koval.trainingplannerbackend.goal.RaceGoalService;
import com.koval.trainingplannerbackend.training.history.AnalyticsService;
import com.koval.trainingplannerbackend.training.history.CompletedSession;
import com.koval.trainingplannerbackend.training.history.CompletedSessionRepository;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/coach")
@CrossOrigin(origins = "*")
public class CoachAnalyticsController {

    private final CoachService coachService;
    private final CompletedSessionRepository sessionRepository;
    private final AnalyticsService analyticsService;
    private final RaceGoalService raceGoalService;

    public CoachAnalyticsController(CoachService coachService,
                                    CompletedSessionRepository sessionRepository,
                                    AnalyticsService analyticsService,
                                    RaceGoalService raceGoalService) {
        this.coachService = coachService;
        this.sessionRepository = sessionRepository;
        this.analyticsService = analyticsService;
        this.raceGoalService = raceGoalService;
    }

    @GetMapping("/athletes/{athleteId}/sessions")
    public ResponseEntity<List<CompletedSession>> getAthleteSessions(@PathVariable String athleteId) {
        String coachId = SecurityUtils.getCurrentUserId();
        if (!coachService.isCoachOfAthlete(coachId, athleteId)) return ResponseEntity.notFound().build();
        return ResponseEntity.ok(sessionRepository.findByUserIdOrderByCompletedAtDesc(athleteId));
    }

    @GetMapping("/athletes/{athleteId}/pmc")
    public ResponseEntity<List<AnalyticsService.PmcDataPoint>> getAthletePmc(
            @PathVariable String athleteId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        String coachId = SecurityUtils.getCurrentUserId();
        if (!coachService.isCoachOfAthlete(coachId, athleteId)) return ResponseEntity.notFound().build();
        return ResponseEntity.ok(analyticsService.generatePmc(athleteId, from, to));
    }

    @GetMapping("/athletes/{athleteId}/goals")
    public ResponseEntity<List<RaceGoalResponse>> getAthleteGoals(@PathVariable String athleteId) {
        String coachId = SecurityUtils.getCurrentUserId();
        if (!coachService.isCoachOfAthlete(coachId, athleteId)) return ResponseEntity.notFound().build();
        return ResponseEntity.ok(raceGoalService.getGoalsForAthlete(athleteId));
    }
}
