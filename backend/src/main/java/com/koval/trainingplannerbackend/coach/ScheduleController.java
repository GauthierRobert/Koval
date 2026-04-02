package com.koval.trainingplannerbackend.coach;

import com.koval.trainingplannerbackend.auth.SecurityUtils;
import com.koval.trainingplannerbackend.club.dto.CalendarClubSessionResponse;
import com.koval.trainingplannerbackend.club.session.ClubSessionService;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
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

@RestController
@RequestMapping("/api/schedule")
public class ScheduleController {

    private final ScheduledWorkoutService scheduledWorkoutService;
    private final ScheduleService scheduleService;
    private final ClubSessionService clubSessionService;

    public ScheduleController(ScheduledWorkoutService scheduledWorkoutService,
            ScheduleService scheduleService,
            ClubSessionService clubSessionService) {
        this.scheduledWorkoutService = scheduledWorkoutService;
        this.scheduleService = scheduleService;
        this.clubSessionService = clubSessionService;
    }

    @PostMapping
    public ResponseEntity<ScheduledWorkoutResponse> scheduleWorkout(
            @Valid @RequestBody ScheduleRequest request) {
        String userId = SecurityUtils.getCurrentUserId();
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(scheduleService.scheduleWorkout(userId, request));
    }

    @GetMapping
    public ResponseEntity<List<ScheduledWorkoutResponse>> getMySchedule(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate start,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate end,
            @RequestParam(defaultValue = "false") boolean includeClubSessions) {
        String userId = SecurityUtils.getCurrentUserId();
        return ResponseEntity.ok(scheduleService.getMySchedule(userId, start, end, includeClubSessions));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteScheduledWorkout(@PathVariable String id) {
        String userId = SecurityUtils.getCurrentUserId();
        scheduleService.deleteScheduledWorkout(userId, id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/complete")
    public ResponseEntity<ScheduledWorkoutResponse> markCompleted(@PathVariable String id) {
        ScheduledWorkout updated = scheduleService.markCompleted(id);
        return ResponseEntity.ok(scheduleService.enrichSingle(updated));
    }

    @PatchMapping("/{id}/reschedule")
    public ResponseEntity<ScheduledWorkoutResponse> rescheduleWorkout(
            @PathVariable String id,
            @Valid @RequestBody ScheduleRequest body) {
        String userId = SecurityUtils.getCurrentUserId();
        return ResponseEntity.ok(scheduleService.rescheduleWorkout(userId, id, body.scheduledDate()));
    }

    @PostMapping("/{id}/skip")
    public ResponseEntity<ScheduledWorkoutResponse> markSkipped(@PathVariable String id) {
        ScheduledWorkout updated = scheduledWorkoutService.markSkipped(id);
        return ResponseEntity.ok(scheduleService.enrichSingle(updated));
    }

    @GetMapping("/club-sessions")
    public ResponseEntity<List<CalendarClubSessionResponse>> getMyClubSessions(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate start,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate end) {
        String userId = SecurityUtils.getCurrentUserId();
        return ResponseEntity.ok(clubSessionService.getMyClubSessionsForCalendar(userId, start, end));
    }
}
