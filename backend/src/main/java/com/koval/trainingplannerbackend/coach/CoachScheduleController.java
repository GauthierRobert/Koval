package com.koval.trainingplannerbackend.coach;

import com.koval.trainingplannerbackend.auth.SecurityUtils;
import com.koval.trainingplannerbackend.club.session.ClubTrainingSession;
import com.koval.trainingplannerbackend.club.session.ClubTrainingSessionRepository;
import com.koval.trainingplannerbackend.plan.AthletePlanSummary;
import com.koval.trainingplannerbackend.plan.TrainingPlanService;
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

@RestController
@RequestMapping("/api/coach")
public class CoachScheduleController {

    private final ScheduledWorkoutService scheduledWorkoutService;
    private final ScheduleService scheduleService;
    private final ClubTrainingSessionRepository clubSessionRepository;
    private final TrainingPlanService trainingPlanService;

    public CoachScheduleController(ScheduledWorkoutService scheduledWorkoutService,
                                   ScheduleService scheduleService,
                                   ClubTrainingSessionRepository clubSessionRepository,
                                   TrainingPlanService trainingPlanService) {
        this.scheduledWorkoutService = scheduledWorkoutService;
        this.scheduleService = scheduleService;
        this.clubSessionRepository = clubSessionRepository;
        this.trainingPlanService = trainingPlanService;
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
            return ResponseEntity.ok(scheduleService.getUnifiedSchedule(workouts, athleteId, start, end));
        } else {
            return ResponseEntity.ok(scheduleService.enrichList(workouts));
        }
    }

    @PostMapping("/schedule/{id}/complete")
    public ResponseEntity<ScheduledWorkout> markCompleted(
            @PathVariable String id,
            @RequestBody(required = false) CompletionRequest request) {
        Integer tss = request != null ? request.tss() : null;
        Double intensityFactor = request != null ? request.intensityFactor() : null;
        return ResponseEntity.ok(scheduledWorkoutService.markCompleted(id, tss, intensityFactor));
    }

    @PostMapping("/schedule/{id}/skip")
    public ResponseEntity<ScheduledWorkout> markSkipped(@PathVariable String id) {
        return ResponseEntity.ok(scheduledWorkoutService.markSkipped(id));
    }

    @GetMapping("/athlete/{athleteId}/plans")
    public ResponseEntity<List<AthletePlanSummary>> getAthletePlans(@PathVariable String athleteId) {
        return ResponseEntity.ok(trainingPlanService.listPlansByAthleteWithProgress(athleteId));
    }

    @GetMapping("/session-reminders")
    public ResponseEntity<List<ClubTrainingSession>> getSessionReminders() {
        String userId = SecurityUtils.getCurrentUserId();
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime threeDaysLater = now.plusDays(3);
        List<ClubTrainingSession> sessions = clubSessionRepository
                .findByResponsibleCoachIdAndLinkedTrainingIdIsNullAndScheduledAtBetween(userId, now, threeDaysLater);
        return ResponseEntity.ok(sessions);
    }
}
