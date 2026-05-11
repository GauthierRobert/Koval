package com.koval.trainingplannerbackend.coach;

import com.koval.trainingplannerbackend.auth.SecurityUtils;
import com.koval.trainingplannerbackend.club.session.ClubTrainingSession;
import com.koval.trainingplannerbackend.club.session.ClubTrainingSessionRepository;
import com.koval.trainingplannerbackend.plan.AthletePlanSummary;
import com.koval.trainingplannerbackend.plan.PlanAnalyticsService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@RestController
@RequestMapping("/api/coach")
public class CoachScheduleController {

    private final ScheduledWorkoutService scheduledWorkoutService;
    private final ScheduledWorkoutEnrichmentService enrichmentService;
    private final ClubTrainingSessionRepository clubSessionRepository;
    private final PlanAnalyticsService planAnalyticsService;

    public CoachScheduleController(ScheduledWorkoutService scheduledWorkoutService,
                                   ScheduledWorkoutEnrichmentService enrichmentService,
                                   ClubTrainingSessionRepository clubSessionRepository,
                                   PlanAnalyticsService planAnalyticsService) {
        this.scheduledWorkoutService = scheduledWorkoutService;
        this.enrichmentService = enrichmentService;
        this.clubSessionRepository = clubSessionRepository;
        this.planAnalyticsService = planAnalyticsService;
    }

    public record CompletionRequest(Integer tss, Double intensityFactor) {}

    @GetMapping("/schedule/{athleteId}")
    public ResponseEntity<List<ScheduledWorkoutResponse>> getAthleteSchedule(
            @PathVariable String athleteId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate start,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate end) {

        List<ScheduledWorkout> workouts = (start != null && end != null)
                ? scheduledWorkoutService.getAthleteSchedule(athleteId, start, end)
                : scheduledWorkoutService.getAthleteSchedule(athleteId);
        if (start != null && end != null) {
            return ResponseEntity.ok(enrichmentService.getUnifiedSchedule(workouts, athleteId, start, end));
        } else {
            return ResponseEntity.ok(enrichmentService.enrichList(workouts));
        }
    }

    @PostMapping("/schedule/{id}/complete")
    public ResponseEntity<ScheduledWorkout> markCompleted(
            @PathVariable String id,
            @RequestBody(required = false) CompletionRequest request) {
        Optional<CompletionRequest> req = Optional.ofNullable(request);
        return ResponseEntity.ok(scheduledWorkoutService.markCompleted(id,
                req.map(CompletionRequest::tss).orElse(null),
                req.map(CompletionRequest::intensityFactor).orElse(null)));
    }

    @PostMapping("/schedule/{id}/skip")
    public ResponseEntity<ScheduledWorkout> markSkipped(@PathVariable String id) {
        return ResponseEntity.ok(scheduledWorkoutService.markSkipped(id));
    }

    @GetMapping("/athlete/{athleteId}/plans")
    public ResponseEntity<List<AthletePlanSummary>> getAthletePlans(@PathVariable String athleteId) {
        return ResponseEntity.ok(planAnalyticsService.listPlansByAthleteWithProgress(athleteId));
    }

    @GetMapping("/session-reminders")
    public ResponseEntity<List<ClubTrainingSession>> getSessionReminders() {
        String userId = SecurityUtils.getCurrentUserId();
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime threeDaysLater = now.plusDays(3);
        List<ClubTrainingSession> sessions = clubSessionRepository
                .findByResponsibleCoachIdAndLinkedTrainingIdIsNullAndScheduledAtBetween(userId, now, threeDaysLater)
                .stream()
                .filter(s -> s.getRecurringTemplateId() != null)
                .filter(s -> s.getLinkedTrainings() == null || s.getLinkedTrainings().isEmpty())
                .toList();
        return ResponseEntity.ok(sessions);
    }
}
