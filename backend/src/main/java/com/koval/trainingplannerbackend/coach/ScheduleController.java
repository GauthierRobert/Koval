package com.koval.trainingplannerbackend.coach;

import com.koval.trainingplannerbackend.auth.SecurityUtils;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
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
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/schedule")
@CrossOrigin(origins = "*")
public class ScheduleController {

    private final ScheduledWorkoutRepository scheduledWorkoutRepository;
    private final CoachService coachService;
    private final ScheduleService scheduleService;

    public ScheduleController(ScheduledWorkoutRepository scheduledWorkoutRepository,
            CoachService coachService,
            ScheduleService scheduleService) {
        this.scheduledWorkoutRepository = scheduledWorkoutRepository;
        this.coachService = coachService;
        this.scheduleService = scheduleService;
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
        return ResponseEntity.status(HttpStatus.CREATED).body(scheduleService.enrichSingle(saved));
    }

    @GetMapping
    public ResponseEntity<List<ScheduledWorkoutResponse>> getMySchedule(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate start,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate end) {
        String userId = SecurityUtils.getCurrentUserId();

        List<ScheduledWorkout> workouts = scheduledWorkoutRepository
                .findByAthleteIdAndScheduledDateBetween(userId, start.minusDays(1), end.plusDays(1));
        return ResponseEntity.ok(scheduleService.enrichList(workouts));
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
        ScheduledWorkout updated = scheduleService.markCompleted(id);
        if (updated == null) return ResponseEntity.notFound().build();
        return ResponseEntity.ok(scheduleService.enrichSingle(updated));
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
                    return ResponseEntity.ok(scheduleService.enrichSingle(saved));
                })
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping("/{id}/skip")
    public ResponseEntity<ScheduledWorkoutResponse> markSkipped(@PathVariable String id) {
        try {
            ScheduledWorkout updated = coachService.markSkipped(id);
            return ResponseEntity.ok(scheduleService.enrichSingle(updated));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        }
    }
}
