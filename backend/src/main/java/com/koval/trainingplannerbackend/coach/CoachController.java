package com.koval.trainingplannerbackend.coach;

import com.koval.trainingplannerbackend.auth.SecurityUtils;
import com.koval.trainingplannerbackend.auth.User;
import com.koval.trainingplannerbackend.training.history.AnalyticsService;
import com.koval.trainingplannerbackend.training.history.CompletedSession;
import com.koval.trainingplannerbackend.training.history.CompletedSessionRepository;
import com.koval.trainingplannerbackend.training.tag.Tag;
import com.koval.trainingplannerbackend.training.tag.TagService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/coach")
@CrossOrigin(origins = "*")
public class CoachController {

    private final CoachService coachService;
    private final ScheduleController scheduleController;
    private final TagService tagService;
    private final CompletedSessionRepository sessionRepository;
    private final AnalyticsService analyticsService;

    public CoachController(CoachService coachService, ScheduleController scheduleController,
                           TagService tagService, CompletedSessionRepository sessionRepository,
                           AnalyticsService analyticsService) {
        this.coachService = coachService;
        this.scheduleController = scheduleController;
        this.tagService = tagService;
        this.sessionRepository = sessionRepository;
        this.analyticsService = analyticsService;
    }

    public record AssignmentRequest(
            String trainingId,
            List<String> athleteIds,
            LocalDate scheduledDate,
            String notes,
            Integer tss,
            Double intensityFactor
    ) {}

    @PostMapping("/assign")
    public ResponseEntity<List<ScheduledWorkout>> assignTraining(
            @RequestBody AssignmentRequest request) {
        String coachId = SecurityUtils.getCurrentUserId();
        try {
            List<ScheduledWorkout> assignments = coachService.assignTraining(
                    coachId,
                    request.trainingId(),
                    request.athleteIds(),
                    request.scheduledDate(),
                    request.notes());
            return ResponseEntity.ok(assignments);
        } catch (IllegalStateException e) {
            return ResponseEntity.badRequest().build();
        }
    }

    @DeleteMapping("/assign/{id}")
    public ResponseEntity<Void> unassignTraining(@PathVariable String id) {
        try {
            coachService.unassignTraining(id);
            return ResponseEntity.noContent().build();
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        }
    }

    @GetMapping("/athletes")
    public ResponseEntity<List<Map<String, Object>>> getAthletes() {
        String coachId = SecurityUtils.getCurrentUserId();
        List<User> athletes = coachService.getCoachAthletes(coachId);
        List<Tag> coachTags = tagService.getTagsForCoach(coachId);

        List<Map<String, Object>> enriched = athletes.stream().map(athlete -> {
            Map<String, Object> map = new HashMap<>();
            map.put("id", athlete.getId());
            map.put("displayName", athlete.getDisplayName());
            map.put("profilePicture", athlete.getProfilePicture());
            map.put("role", athlete.getRole().name());
            map.put("ftp", athlete.getFtp());
            List<String> athleteTagNames = coachTags.stream()
                    .filter(tag -> tag.getAthleteIds().contains(athlete.getId()))
                    .map(Tag::getName)
                    .toList();
            map.put("tags", athleteTagNames);
            map.put("hasCoach", true);
            return map;
        }).toList();

        return ResponseEntity.ok(enriched);
    }

    @DeleteMapping("/athletes/{athleteId}")
    public ResponseEntity<Void> removeAthlete(@PathVariable String athleteId) {
        String coachId = SecurityUtils.getCurrentUserId();
        coachService.removeAthlete(coachId, athleteId);
        return ResponseEntity.noContent().build();
    }

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

    public record CompletionRequest(Integer tss, Double intensityFactor) {}

    @PostMapping("/schedule/{id}/complete")
    public ResponseEntity<ScheduledWorkout> markCompleted(
            @PathVariable String id,
            @RequestBody(required = false) CompletionRequest request) {
        try {
            Integer tss = request != null ? request.tss() : null;
            Double intensityFactor = request != null ? request.intensityFactor() : null;
            return ResponseEntity.ok(coachService.markCompleted(id, tss, intensityFactor));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        }
    }

    @PostMapping("/schedule/{id}/skip")
    public ResponseEntity<ScheduledWorkout> markSkipped(@PathVariable String id) {
        try {
            return ResponseEntity.ok(coachService.markSkipped(id));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        }
    }

    // --- Tag management endpoints ---

    @PutMapping("/athletes/{athleteId}/tags")
    public ResponseEntity<List<Tag>> setAthleteTags(
            @PathVariable String athleteId,
            @RequestBody Map<String, List<String>> body) {
        String coachId = SecurityUtils.getCurrentUserId();
        try {
            List<String> tagIds = body.get("tags");
            if (tagIds == null) tagIds = List.of();
            return ResponseEntity.ok(coachService.setAthleteTags(coachId, athleteId, tagIds));
        } catch (IllegalStateException e) {
            return ResponseEntity.badRequest().build();
        }
    }

    @PostMapping("/athletes/{athleteId}/tags")
    public ResponseEntity<Tag> addAthleteTag(
            @PathVariable String athleteId,
            @RequestBody Map<String, String> body) {
        String coachId = SecurityUtils.getCurrentUserId();
        try {
            String tagName = body.get("tag");
            if (tagName == null || tagName.isBlank()) return ResponseEntity.badRequest().build();
            return ResponseEntity.ok(coachService.addTagToAthlete(coachId, athleteId, tagName));
        } catch (IllegalStateException e) {
            return ResponseEntity.badRequest().build();
        }
    }

    @DeleteMapping("/athletes/{athleteId}/tags/{tagId}")
    public ResponseEntity<Tag> removeAthleteTag(
            @PathVariable String athleteId,
            @PathVariable String tagId) {
        String coachId = SecurityUtils.getCurrentUserId();
        try {
            return ResponseEntity.ok(coachService.removeTagFromAthlete(coachId, athleteId, tagId));
        } catch (IllegalStateException e) {
            return ResponseEntity.badRequest().build();
        }
    }

    @GetMapping("/athletes/tags")
    public ResponseEntity<List<Tag>> getAllTags() {
        String coachId = SecurityUtils.getCurrentUserId();
        return ResponseEntity.ok(coachService.getAthleteTagsForCoach(coachId));
    }

    // --- Athlete analytics endpoints ---

    @GetMapping("/athletes/{athleteId}/sessions")
    public ResponseEntity<List<CompletedSession>> getAthleteSessions(@PathVariable String athleteId) {
        String coachId = SecurityUtils.getCurrentUserId();
        // Validate coach owns this athlete
        List<User> athletes = coachService.getCoachAthletes(coachId);
        boolean owns = athletes.stream().anyMatch(a -> a.getId().equals(athleteId));
        if (!owns) return ResponseEntity.notFound().build();
        return ResponseEntity.ok(sessionRepository.findByUserIdOrderByCompletedAtDesc(athleteId));
    }

    @GetMapping("/athletes/{athleteId}/pmc")
    public ResponseEntity<List<AnalyticsService.PmcDataPoint>> getAthletePmc(
            @PathVariable String athleteId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        String coachId = SecurityUtils.getCurrentUserId();
        List<User> athletes = coachService.getCoachAthletes(coachId);
        boolean owns = athletes.stream().anyMatch(a -> a.getId().equals(athleteId));
        if (!owns) return ResponseEntity.notFound().build();
        return ResponseEntity.ok(analyticsService.generatePmc(athleteId, from, to));
    }

    // --- Invite Code endpoints ---

    public record InviteCodeRequest(List<String> tags, int maxUses, LocalDateTime expiresAt) {}

    public record RedeemRequest(String code) {}

    @PostMapping("/invite-codes")
    public ResponseEntity<InviteCode> generateInviteCode(
            @RequestBody InviteCodeRequest request) {
        String coachId = SecurityUtils.getCurrentUserId();
        try {
            InviteCode code = coachService.generateInviteCode(
                    coachId, request.tags(), request.maxUses(), request.expiresAt());
            return ResponseEntity.ok(code);
        } catch (IllegalStateException e) {
            return ResponseEntity.badRequest().build();
        }
    }

    @GetMapping("/invite-codes")
    public ResponseEntity<List<InviteCode>> getInviteCodes() {
        String coachId = SecurityUtils.getCurrentUserId();
        return ResponseEntity.ok(coachService.getInviteCodes(coachId));
    }

    @DeleteMapping("/invite-codes/{id}")
    public ResponseEntity<Void> deactivateInviteCode(@PathVariable String id) {
        String coachId = SecurityUtils.getCurrentUserId();
        try {
            coachService.deactivateInviteCode(coachId, id);
            return ResponseEntity.noContent().build();
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        } catch (IllegalStateException e) {
            return ResponseEntity.badRequest().build();
        }
    }

    @PostMapping("/redeem-invite")
    public ResponseEntity<User> redeemInviteCode(@RequestBody RedeemRequest request) {
        String userId = SecurityUtils.getCurrentUserId();
        try {
            User updated = coachService.redeemInviteCode(userId, request.code());
            return ResponseEntity.ok(updated);
        } catch (IllegalArgumentException | IllegalStateException e) {
            return ResponseEntity.badRequest().build();
        }
    }
}
