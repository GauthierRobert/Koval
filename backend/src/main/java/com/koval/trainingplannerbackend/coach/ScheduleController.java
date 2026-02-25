package com.koval.trainingplannerbackend.coach;

import com.koval.trainingplannerbackend.auth.SecurityUtils;
import com.koval.trainingplannerbackend.training.model.Training;
import com.koval.trainingplannerbackend.training.TrainingRepository;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
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

    public ScheduleController(ScheduledWorkoutRepository scheduledWorkoutRepository,
            TrainingRepository trainingRepository,
            CoachService coachService) {
        this.scheduledWorkoutRepository = scheduledWorkoutRepository;
        this.trainingRepository = trainingRepository;
        this.coachService = coachService;
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
        try {
            ScheduledWorkout updated = coachService.markCompleted(id, null, null);
            return ResponseEntity.ok(enrichSingle(updated));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        }
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
