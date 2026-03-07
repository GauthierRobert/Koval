package com.koval.trainingplannerbackend.coach;

import com.koval.trainingplannerbackend.auth.SecurityUtils;
import com.koval.trainingplannerbackend.training.TrainingRepository;
import com.koval.trainingplannerbackend.training.history.AnalyticsService;
import com.koval.trainingplannerbackend.training.history.CompletedSession;
import com.koval.trainingplannerbackend.training.history.CompletedSessionRepository;
import com.koval.trainingplannerbackend.training.model.Training;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/schedule")
@CrossOrigin(origins = "*")
public class ScheduleController {

    private final ScheduledWorkoutRepository scheduledWorkoutRepository;
    private final TrainingRepository trainingRepository;
    private final CoachService coachService;
    private final CompletedSessionRepository completedSessionRepository;
    private final AnalyticsService analyticsService;

    public ScheduleController(ScheduledWorkoutRepository scheduledWorkoutRepository,
            TrainingRepository trainingRepository,
            CoachService coachService,
            CompletedSessionRepository completedSessionRepository,
            AnalyticsService analyticsService) {
        this.scheduledWorkoutRepository = scheduledWorkoutRepository;
        this.trainingRepository = trainingRepository;
        this.coachService = coachService;
        this.completedSessionRepository = completedSessionRepository;
        this.analyticsService = analyticsService;
    }

    public static class ScheduleRequest {
        private String trainingId;
        private LocalDate scheduledDate;
        private String notes;

        public String getTrainingId() {
            return trainingId;
        }

        public void setTrainingId(String trainingId) {
            this.trainingId = trainingId;
        }

        public LocalDate getScheduledDate() {
            return scheduledDate;
        }

        public void setScheduledDate(LocalDate scheduledDate) {
            this.scheduledDate = scheduledDate;
        }

        public String getNotes() {
            return notes;
        }

        public void setNotes(String notes) {
            this.notes = notes;
        }
    }

    @PostMapping
    public ResponseEntity<ScheduledWorkoutResponse> scheduleWorkout(
            @RequestBody ScheduleRequest request) {
        String userId = SecurityUtils.getCurrentUserId();

        ScheduledWorkout workout = new ScheduledWorkout();
        workout.setTrainingId(request.getTrainingId());
        workout.setAthleteId(userId);
        workout.setAssignedBy(userId);
        workout.setScheduledDate(request.getScheduledDate());
        workout.setNotes(request.getNotes());
        workout.setStatus(ScheduleStatus.PENDING);

        ScheduledWorkout saved = scheduledWorkoutRepository.save(workout);
        return ResponseEntity.ok(enrichSingle(saved));
    }

    @GetMapping
    public ResponseEntity<List<ScheduledWorkoutResponse>> getMySchedule(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate start,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate end) {
        String userId = SecurityUtils.getCurrentUserId();

        List<ScheduledWorkout> workouts = scheduledWorkoutRepository
                .findByAthleteIdAndScheduledDateBetween(userId, start.minusDays(1), end.plusDays(1));
        return ResponseEntity.ok(enrichList(workouts));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteScheduledWorkout(@PathVariable String id) {
        String userId = SecurityUtils.getCurrentUserId();

        return scheduledWorkoutRepository.findById(id)
                .map(workout -> {
                    if (!userId.equals(workout.getAthleteId())) {
                        return ResponseEntity.status(403).<Void>build();
                    }
                    scheduledWorkoutRepository.deleteById(id);
                    return ResponseEntity.noContent().<Void>build();
                })
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping("/{id}/complete")
    public ResponseEntity<ScheduledWorkoutResponse> markCompleted(@PathVariable String id) {
        ScheduledWorkout workout = scheduledWorkoutRepository.findById(id).orElse(null);
        if (workout == null) return ResponseEntity.notFound().build();

        // If a real (non-synthetic) session is already linked, just mark complete
        if (workout.getSessionId() != null) {
            boolean isSynthetic = completedSessionRepository.findById(workout.getSessionId())
                    .map(CompletedSession::isSyntheticCompletion).orElse(false);
            if (!isSynthetic) {
                ScheduledWorkout updated = coachService.markCompleted(id,
                        workout.getTss(), workout.getIntensityFactor(), workout.getSessionId());
                return ResponseEntity.ok(enrichSingle(updated));
            }
            // Delete existing synthetic session before creating a fresh one
            completedSessionRepository.deleteById(workout.getSessionId());
        }

        // Create a synthetic CompletedSession from planned training data
        Training training = trainingRepository.findById(workout.getTrainingId()).orElse(null);
        CompletedSession session = new CompletedSession();
        session.setUserId(workout.getAthleteId());
        session.setSyntheticCompletion(true);
        session.setScheduledWorkoutId(id);
        session.setCompletedAt(workout.getScheduledDate() != null
                ? workout.getScheduledDate().atTime(12, 0) : LocalDateTime.now());

        if (training != null) {
            session.setTitle(training.getTitle());
            session.setSportType(training.getSportType() != null ? training.getSportType().name() : "CYCLING");
            if (training.getEstimatedDurationSeconds() != null)
                session.setTotalDurationSeconds(training.getEstimatedDurationSeconds());
            if (training.getEstimatedTss() != null)
                session.setTss(training.getEstimatedTss().doubleValue());
            if (training.getEstimatedIf() != null)
                session.setIntensityFactor(training.getEstimatedIf());
        }

        CompletedSession saved = completedSessionRepository.save(session);
        analyticsService.recomputeAndSaveUserLoad(workout.getAthleteId());

        ScheduledWorkout updated = coachService.markCompleted(id,
                saved.getTss() != null ? saved.getTss().intValue() : null,
                saved.getIntensityFactor(),
                saved.getId());
        return ResponseEntity.ok(enrichSingle(updated));
    }

    @PatchMapping("/{id}/reschedule")
    public ResponseEntity<ScheduledWorkoutResponse> rescheduleWorkout(
            @PathVariable String id,
            @RequestBody Map<String, String> body) {
        String userId = SecurityUtils.getCurrentUserId();
        String newDate = body.get("scheduledDate");
        if (newDate == null) {
            return ResponseEntity.badRequest().build();
        }

        return scheduledWorkoutRepository.findById(id)
                .map(workout -> {
                    boolean isOwner = userId.equals(workout.getAthleteId());
                    boolean isAssigner = userId.equals(workout.getAssignedBy());
                    if (!isOwner && !isAssigner) {
                        return ResponseEntity.status(403).<ScheduledWorkoutResponse>build();
                    }
                    if (workout.getStatus() != ScheduleStatus.PENDING) {
                        return ResponseEntity.badRequest().<ScheduledWorkoutResponse>build();
                    }
                    workout.setScheduledDate(LocalDate.parse(newDate));
                    ScheduledWorkout saved = scheduledWorkoutRepository.save(workout);
                    return ResponseEntity.ok(enrichSingle(saved));
                })
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping("/{id}/skip")
    public ResponseEntity<ScheduledWorkoutResponse> markSkipped(@PathVariable String id) {
        try {
            ScheduledWorkout updated = coachService.markSkipped(id);
            return ResponseEntity.ok(enrichSingle(updated));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        }
    }

    // --- Enrichment helpers ---

    List<ScheduledWorkoutResponse> enrichList(List<ScheduledWorkout> workouts) {
        if (workouts.isEmpty())
            return List.of();

        List<String> trainingIds = workouts.stream()
                .map(ScheduledWorkout::getTrainingId)
                .distinct()
                .toList();

        Map<String, Training> trainingsMap = trainingRepository.findAllById(trainingIds).stream()
                .collect(Collectors.toMap(Training::getId, Function.identity()));

        return workouts.stream()
                .map(sw -> {
                    Training t = trainingsMap.get(sw.getTrainingId());
                    String title = t != null ? t.getTitle() : null;
                    var type = t != null ? t.getTrainingType() : null;
                    Integer duration = t != null ? t.getEstimatedDurationSeconds() : null;
                    var sport = t != null ? t.getSportType() : null;
                    Integer estimatedTss = t != null ? t.getEstimatedTss() : null;
                    Double estimatedIf = t != null ? t.getEstimatedIf() : null;
                    return ScheduledWorkoutResponse.from(sw, title, type, duration, sport, estimatedTss, estimatedIf);
                })
                .toList();
    }

    private ScheduledWorkoutResponse enrichSingle(ScheduledWorkout sw) {
        return trainingRepository.findById(sw.getTrainingId())
                .map(t -> ScheduledWorkoutResponse.from(sw, t.getTitle(), t.getTrainingType(),
                        t.getEstimatedDurationSeconds(), t.getSportType(), t.getEstimatedTss(), t.getEstimatedIf()))
                .orElse(ScheduledWorkoutResponse.from(sw, null, null, null, null, null, null));
    }
}
