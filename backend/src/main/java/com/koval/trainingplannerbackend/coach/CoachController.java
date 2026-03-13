package com.koval.trainingplannerbackend.coach;

import com.koval.trainingplannerbackend.auth.SecurityUtils;
import com.koval.trainingplannerbackend.auth.User;
import com.koval.trainingplannerbackend.club.ClubInviteCode;
import com.koval.trainingplannerbackend.club.ClubInviteCodeRepository;
import com.koval.trainingplannerbackend.club.ClubService;
import com.koval.trainingplannerbackend.goal.RaceGoal;
import com.koval.trainingplannerbackend.goal.RaceGoalService;
import com.koval.trainingplannerbackend.training.history.AnalyticsService;
import com.koval.trainingplannerbackend.training.history.CompletedSession;
import com.koval.trainingplannerbackend.training.history.CompletedSessionRepository;
import com.koval.trainingplannerbackend.training.group.Group;
import com.koval.trainingplannerbackend.training.group.GroupService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api/coach")
@CrossOrigin(origins = "*")
public class CoachController {

    private final CoachService coachService;
    private final ScheduleService scheduleService;
    private final GroupService groupService;
    private final CompletedSessionRepository sessionRepository;
    private final AnalyticsService analyticsService;
    private final RaceGoalService raceGoalService;
    private final ClubService clubService;
    private final InviteCodeRepository inviteCodeRepository;
    private final ClubInviteCodeRepository clubInviteCodeRepository;

    public CoachController(CoachService coachService, ScheduleService scheduleService,
                           GroupService groupService, CompletedSessionRepository sessionRepository,
                           AnalyticsService analyticsService, RaceGoalService raceGoalService,
                           ClubService clubService, InviteCodeRepository inviteCodeRepository,
                           ClubInviteCodeRepository clubInviteCodeRepository) {
        this.coachService = coachService;
        this.scheduleService = scheduleService;
        this.groupService = groupService;
        this.sessionRepository = sessionRepository;
        this.analyticsService = analyticsService;
        this.raceGoalService = raceGoalService;
        this.clubService = clubService;
        this.inviteCodeRepository = inviteCodeRepository;
        this.clubInviteCodeRepository = clubInviteCodeRepository;
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
        List<Group> coachGroups = groupService.getGroupsForCoach(coachId);

        List<Map<String, Object>> enriched = athletes.stream().map(athlete -> {
            Map<String, Object> map = new HashMap<>();
            map.put("id", athlete.getId());
            map.put("displayName", athlete.getDisplayName());
            map.put("profilePicture", athlete.getProfilePicture());
            map.put("role", athlete.getRole().name());
            map.put("ftp", athlete.getFtp());
            List<String> athleteGroupNames = coachGroups.stream()
                    .filter(group -> group.getAthleteIds().contains(athlete.getId()))
                    .map(Group::getName)
                    .toList();
            map.put("groups", athleteGroupNames);
            map.put("hasCoach", true);
            return map;
        }).toList();

        return ResponseEntity.ok(enriched);
    }

    @GetMapping(value = "/athletes", params = "page")
    public ResponseEntity<Page<Map<String, Object>>> getAthletes(Pageable pageable) {
        String coachId = SecurityUtils.getCurrentUserId();
        List<User> athletes = coachService.getCoachAthletes(coachId);
        List<Group> coachGroups = groupService.getGroupsForCoach(coachId);

        List<Map<String, Object>> enriched = athletes.stream().map(athlete -> {
            Map<String, Object> map = new HashMap<>();
            map.put("id", athlete.getId());
            map.put("displayName", athlete.getDisplayName());
            map.put("profilePicture", athlete.getProfilePicture());
            map.put("role", athlete.getRole().name());
            map.put("ftp", athlete.getFtp());
            List<String> athleteGroupNames = coachGroups.stream()
                    .filter(group -> group.getAthleteIds().contains(athlete.getId()))
                    .map(Group::getName)
                    .toList();
            map.put("groups", athleteGroupNames);
            map.put("hasCoach", true);
            return map;
        }).toList();

        int start = (int) pageable.getOffset();
        int end = Math.min(start + pageable.getPageSize(), enriched.size());
        List<Map<String, Object>> pageContent = start >= enriched.size() ? List.of() : enriched.subList(start, end);
        return ResponseEntity.ok(new PageImpl<>(pageContent, pageable, enriched.size()));
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
        return ResponseEntity.ok(scheduleService.enrichList(workouts));
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

    // --- Group management endpoints ---

    @PutMapping("/athletes/{athleteId}/groups")
    public ResponseEntity<List<Group>> setAthleteGroups(
            @PathVariable String athleteId,
            @RequestBody Map<String, List<String>> body) {
        String coachId = SecurityUtils.getCurrentUserId();
        try {
            List<String> groupIds = body.get("groups");
            if (groupIds == null) groupIds = List.of();
            return ResponseEntity.ok(coachService.setAthleteGroups(coachId, athleteId, groupIds));
        } catch (IllegalStateException e) {
            return ResponseEntity.badRequest().build();
        }
    }

    @PostMapping("/athletes/{athleteId}/groups")
    public ResponseEntity<Group> addAthleteGroup(
            @PathVariable String athleteId,
            @RequestBody Map<String, String> body) {
        String coachId = SecurityUtils.getCurrentUserId();
        try {
            String groupName = body.get("group");
            if (groupName == null || groupName.isBlank()) return ResponseEntity.badRequest().build();
            return ResponseEntity.ok(coachService.addGroupToAthlete(coachId, athleteId, groupName));
        } catch (IllegalStateException e) {
            return ResponseEntity.badRequest().build();
        }
    }

    @DeleteMapping("/athletes/{athleteId}/groups/{groupName}")
    public ResponseEntity<Group> removeAthleteGroup(
            @PathVariable String athleteId,
            @PathVariable String groupName) {
        String coachId = SecurityUtils.getCurrentUserId();
        try {
            return ResponseEntity.ok(coachService.removeGroupFromAthlete(coachId, athleteId, groupName));
        } catch (IllegalStateException e) {
            return ResponseEntity.badRequest().build();
        }
    }

    @GetMapping("/athletes/groups")
    public ResponseEntity<List<Group>> getAllGroups() {
        String coachId = SecurityUtils.getCurrentUserId();
        return ResponseEntity.ok(coachService.getAthleteGroupsForCoach(coachId));
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

    @GetMapping("/athletes/{athleteId}/goals")
    public ResponseEntity<List<RaceGoal>> getAthleteGoals(@PathVariable String athleteId) {
        String coachId = SecurityUtils.getCurrentUserId();
        List<User> athletes = coachService.getCoachAthletes(coachId);
        boolean owns = athletes.stream().anyMatch(a -> a.getId().equals(athleteId));
        if (!owns) return ResponseEntity.notFound().build();
        return ResponseEntity.ok(raceGoalService.getGoalsForAthlete(athleteId));
    }

    // --- Invite Code endpoints ---

    public record InviteCodeRequest(List<String> groups, int maxUses, LocalDateTime expiresAt, String code) {}

    public record RedeemRequest(String code) {}

    public record RedeemResponse(String type, String message) {}

    @PostMapping("/invite-codes")
    public ResponseEntity<InviteCode> generateInviteCode(
            @RequestBody InviteCodeRequest request) {
        String coachId = SecurityUtils.getCurrentUserId();
        try {
            InviteCode code = coachService.generateInviteCode(
                    coachId, request.groups(), request.maxUses(), request.expiresAt(), request.code());
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
    public ResponseEntity<?> redeemInviteCode(@RequestBody RedeemRequest request) {
        String userId = SecurityUtils.getCurrentUserId();
        String normalizedCode = request.code().toUpperCase().trim();

        // Look up in coach invite codes
        Optional<InviteCode> coachCode = inviteCodeRepository.findByCode(normalizedCode);
        if (coachCode.isPresent()) {
            try {
                coachService.redeemInviteCode(userId, normalizedCode);
                return ResponseEntity.ok(new RedeemResponse("GROUP", "Joined training group"));
            } catch (IllegalStateException e) {
                return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
            }
        }

        // Look up in club invite codes
        Optional<ClubInviteCode> clubCode = clubInviteCodeRepository.findByCode(normalizedCode);
        if (clubCode.isPresent()) {
            try {
                clubService.redeemClubInviteCode(userId, normalizedCode);
                return ResponseEntity.ok(new RedeemResponse("CLUB", "Joined club successfully"));
            } catch (IllegalStateException e) {
                return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
            }
        }

        return ResponseEntity.badRequest().body(Map.of("error", "Invalid invite code"));
    }
}
