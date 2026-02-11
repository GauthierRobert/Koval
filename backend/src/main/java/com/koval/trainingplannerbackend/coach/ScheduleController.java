package com.koval.trainingplannerbackend.coach;

import com.koval.trainingplannerbackend.training.Training;
import com.koval.trainingplannerbackend.training.TrainingRepository;
import com.koval.trainingplannerbackend.training.WorkoutBlock;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * REST Controller for athlete self-scheduling.
 * No role check — any authenticated user can schedule workouts for themselves.
 */
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

        public String getTrainingId() { return trainingId; }
        public void setTrainingId(String trainingId) { this.trainingId = trainingId; }
        public LocalDate getScheduledDate() { return scheduledDate; }
        public void setScheduledDate(LocalDate scheduledDate) { this.scheduledDate = scheduledDate; }
        public String getNotes() { return notes; }
        public void setNotes(String notes) { this.notes = notes; }
    }

    /**
     * Schedule a workout for the current user (self-assignment).
     */
    @PostMapping
    public ResponseEntity<ScheduledWorkoutResponse> scheduleWorkout(
            @RequestBody ScheduleRequest request,
            @RequestHeader("X-User-Id") String userId) {

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

    /**
     * Get current user's scheduled workouts by date range.
     */
    @GetMapping
    public ResponseEntity<List<ScheduledWorkoutResponse>> getMySchedule(
            @RequestHeader("X-User-Id") String userId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate start,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate end) {

        List<ScheduledWorkout> workouts = scheduledWorkoutRepository
                .findByAthleteIdAndScheduledDateBetween(userId, start, end);
        return ResponseEntity.ok(enrichList(workouts));
    }

    /**
     * Delete a scheduled workout (must be owned by the user).
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteScheduledWorkout(
            @PathVariable String id,
            @RequestHeader("X-User-Id") String userId) {

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

    /**
     * Mark a scheduled workout as completed.
     */
    @PostMapping("/{id}/complete")
    public ResponseEntity<ScheduledWorkoutResponse> markCompleted(@PathVariable String id) {
        try {
            ScheduledWorkout updated = coachService.markCompleted(id, null, null);
            return ResponseEntity.ok(enrichSingle(updated));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        }
    }

    /**
     * Mark a scheduled workout as skipped.
     */
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
        if (workouts.isEmpty()) return List.of();

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
                    Integer duration = t != null && t.getBlocks() != null
                            ? t.getBlocks().stream().mapToInt(WorkoutBlock::getDurationSeconds).sum()
                            : null;
                    return ScheduledWorkoutResponse.from(sw, title, duration);
                })
                .toList();
    }

    private ScheduledWorkoutResponse enrichSingle(ScheduledWorkout sw) {
        return trainingRepository.findById(sw.getTrainingId())
                .map(t -> {
                    Integer duration = t.getBlocks() != null
                            ? t.getBlocks().stream().mapToInt(WorkoutBlock::getDurationSeconds).sum()
                            : null;
                    return ScheduledWorkoutResponse.from(sw, t.getTitle(), duration);
                })
                .orElse(ScheduledWorkoutResponse.from(sw, null, null));
    }
}
