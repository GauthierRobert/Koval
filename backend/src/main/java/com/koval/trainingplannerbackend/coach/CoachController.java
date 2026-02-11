package com.koval.trainingplannerbackend.coach;

import com.koval.trainingplannerbackend.auth.User;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * REST Controller for Coach-specific operations.
 */
@RestController
@RequestMapping("/api/coach")
@CrossOrigin(origins = "*")
public class CoachController {

    private final CoachService coachService;
    private final ScheduleController scheduleController;

    public CoachController(CoachService coachService, ScheduleController scheduleController) {
        this.coachService = coachService;
        this.scheduleController = scheduleController;
    }

    /**
     * DTO for assignment request.
     */
    public static class AssignmentRequest {
        private String trainingId;
        private List<String> athleteIds;
        private LocalDate scheduledDate;
        private String notes;
        private Integer tss;
        private Double intensityFactor;

        // Getters and Setters
        public String getTrainingId() {
            return trainingId;
        }

        public void setTrainingId(String trainingId) {
            this.trainingId = trainingId;
        }

        public List<String> getAthleteIds() {
            return athleteIds;
        }

        public void setAthleteIds(List<String> athleteIds) {
            this.athleteIds = athleteIds;
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

        public Integer getTss() {
            return tss;
        }

        public void setTss(Integer tss) {
            this.tss = tss;
        }

        public Double getIntensityFactor() {
            return intensityFactor;
        }

        public void setIntensityFactor(Double intensityFactor) {
            this.intensityFactor = intensityFactor;
        }
    }

    /**
     * Assign a training to athletes.
     * TODO: Get coachId from JWT token in real implementation
     */
    @PostMapping("/assign")
    public ResponseEntity<List<ScheduledWorkout>> assignTraining(
            @RequestBody AssignmentRequest request,
            @RequestHeader(value = "X-User-Id") String coachId) {
        try {
            List<ScheduledWorkout> assignments = coachService.assignTraining(
                    coachId,
                    request.getTrainingId(),
                    request.getAthleteIds(),
                    request.getScheduledDate(),
                    request.getNotes());
            return ResponseEntity.ok(assignments);
        } catch (IllegalStateException e) {
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * Unassign a scheduled workout.
     */
    @DeleteMapping("/assign/{id}")
    public ResponseEntity<Void> unassignTraining(@PathVariable String id) {
        try {
            coachService.unassignTraining(id);
            return ResponseEntity.noContent().build();
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        }
    }

    /**
     * Get coach's athletes.
     */
    @GetMapping("/athletes")
    public ResponseEntity<List<User>> getAthletes(
            @RequestHeader(value = "X-User-Id") String coachId) {
        return ResponseEntity.ok(coachService.getCoachAthletes(coachId));
    }

    /**
     * Add an athlete to coach's roster.
     */
    @PostMapping("/athletes/{athleteId}")
    public ResponseEntity<Void> addAthlete(
            @PathVariable String athleteId,
            @RequestHeader(value = "X-User-Id") String coachId) {
        try {
            coachService.addAthlete(coachId, athleteId);
            return ResponseEntity.ok().build();
        } catch (IllegalStateException e) {
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * Remove an athlete from coach's roster.
     */
    @DeleteMapping("/athletes/{athleteId}")
    public ResponseEntity<Void> removeAthlete(
            @PathVariable String athleteId,
            @RequestHeader(value = "X-User-Id") String coachId) {
        coachService.removeAthlete(coachId, athleteId);
        return ResponseEntity.noContent().build();
    }

    /**
     * Get an athlete's schedule (enriched with training metadata).
     */
    @GetMapping("/schedule/{athleteId}")
    public ResponseEntity<List<ScheduledWorkoutResponse>> getAthleteSchedule(
            @PathVariable String athleteId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate start,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate end) {

        List<ScheduledWorkout> workouts = (start != null && end != null)
                ? coachService.getAthleteSchedule(athleteId, start, end)
                : coachService.getAthleteSchedule(athleteId);
        return ResponseEntity.ok(scheduleController.enrichList(workouts));
    }

    public static class CompletionRequest {
        private Integer tss;
        private Double intensityFactor;

        // Getters and Setters
        public Integer getTss() {
            return tss;
        }

        public void setTss(Integer tss) {
            this.tss = tss;
        }

        public Double getIntensityFactor() {
            return intensityFactor;
        }

        public void setIntensityFactor(Double intensityFactor) {
            this.intensityFactor = intensityFactor;
        }
    }

    /**
     * Mark a scheduled workout as completed.
     */
    @PostMapping("/schedule/{id}/complete")
    public ResponseEntity<ScheduledWorkout> markCompleted(
            @PathVariable String id,
            @RequestBody(required = false) CompletionRequest request) {
        try {
            Integer tss = request != null ? request.getTss() : null;
            Double intensityFactor = request != null ? request.getIntensityFactor() : null;
            return ResponseEntity.ok(coachService.markCompleted(id, tss, intensityFactor));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        }
    }

    /**
     * Mark a scheduled workout as skipped.
     */
    @PostMapping("/schedule/{id}/skip")
    public ResponseEntity<ScheduledWorkout> markSkipped(@PathVariable String id) {
        try {
            return ResponseEntity.ok(coachService.markSkipped(id));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        }
    }

    // --- Tag management endpoints ---

    /**
     * Replace all tags for an athlete.
     */
    @PutMapping("/athletes/{athleteId}/tags")
    public ResponseEntity<User> setAthleteTags(
            @PathVariable String athleteId,
            @RequestBody Map<String, List<String>> body,
            @RequestHeader(value = "X-User-Id") String coachId) {
        try {
            List<String> tags = body.get("tags");
            if (tags == null) tags = List.of();
            return ResponseEntity.ok(coachService.setAthleteTags(coachId, athleteId, tags));
        } catch (IllegalStateException e) {
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * Add a single tag to an athlete.
     */
    @PostMapping("/athletes/{athleteId}/tags")
    public ResponseEntity<User> addAthleteTag(
            @PathVariable String athleteId,
            @RequestBody Map<String, String> body,
            @RequestHeader(value = "X-User-Id") String coachId) {
        try {
            String tag = body.get("tag");
            if (tag == null || tag.isBlank()) return ResponseEntity.badRequest().build();
            return ResponseEntity.ok(coachService.addTagToAthlete(coachId, athleteId, tag));
        } catch (IllegalStateException e) {
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * Remove a single tag from an athlete.
     */
    @DeleteMapping("/athletes/{athleteId}/tags/{tag}")
    public ResponseEntity<User> removeAthleteTag(
            @PathVariable String athleteId,
            @PathVariable String tag,
            @RequestHeader(value = "X-User-Id") String coachId) {
        try {
            return ResponseEntity.ok(coachService.removeTagFromAthlete(coachId, athleteId, tag));
        } catch (IllegalStateException e) {
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * Get all unique tags across the coach's athletes.
     */
    @GetMapping("/athletes/tags")
    public ResponseEntity<List<String>> getAllTags(
            @RequestHeader(value = "X-User-Id") String coachId) {
        return ResponseEntity.ok(coachService.getAthleteTagsForCoach(coachId));
    }
}
