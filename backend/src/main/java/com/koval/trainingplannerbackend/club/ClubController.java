package com.koval.trainingplannerbackend.club;

import com.koval.trainingplannerbackend.auth.SecurityUtils;
import org.springframework.data.domain.PageRequest;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.DayOfWeek;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

@RestController
@RequestMapping("/api/clubs")
public class ClubController {

    private final ClubService clubService;
    private final RecurringSessionService recurringSessionService;

    public ClubController(ClubService clubService, RecurringSessionService recurringSessionService) {
        this.clubService = clubService;
        this.recurringSessionService = recurringSessionService;
    }

    // DTOs
    public record CreateClubRequest(String name, String description, String location,
                                    String logoUrl, ClubVisibility visibility) {}

    public record CreateSessionRequest(String title, String sport, LocalDateTime scheduledAt,
                                       String location, String description, String linkedTrainingId,
                                       Integer maxParticipants, Integer durationMinutes,
                                       String clubGroupId, String responsibleCoachId,
                                       boolean openToAll, Integer openToAllDelayValue,
                                       OpenToAllDelayUnit openToAllDelayUnit) {}

    public record CreateRecurringSessionRequest(String title, String sport, DayOfWeek dayOfWeek,
                                                 LocalTime timeOfDay, String location, String description,
                                                 String linkedTrainingId, Integer maxParticipants,
                                                 Integer durationMinutes, String clubGroupId,
                                                 String responsibleCoachId,
                                                 boolean openToAll, Integer openToAllDelayValue,
                                                 OpenToAllDelayUnit openToAllDelayUnit) {}

    public record LinkTrainingRequest(String trainingId) {}

    public record CreateGroupRequest(String name) {}

    public record UpdateMemberRoleRequest(ClubMemberRole role) {}

    public record CreateInviteCodeRequest(String clubGroupId, int maxUses, String expiresAt) {}

    public record ClubInviteCodeResponse(String id, String code, String clubId, String createdBy,
                                          String clubGroupId, String clubGroupName,
                                          int maxUses, int currentUses,
                                          String expiresAt, boolean active, String createdAt) {}

    public record RedeemInviteCodeRequest(String code) {}

    public record ClubSummaryResponse(String id, String name, String description, String logoUrl,
                                      ClubVisibility visibility, int memberCount, String membershipStatus) {}

    public record ClubDetailResponse(String id, String name, String description, String location,
                                     String logoUrl, ClubVisibility visibility, int memberCount,
                                     String ownerId, String currentMembershipStatus,
                                     ClubMemberRole currentMemberRole, LocalDateTime createdAt) {}

    public record ClubMemberResponse(String membershipId, String userId, String displayName,
                                     String profilePicture, ClubMemberRole role,
                                     LocalDateTime joinedAt, List<String> tags) {}

    public record LeaderboardEntry(String userId, String displayName, String profilePicture,
                                   double weeklyTss, int sessionCount, int rank) {}

    public record ClubWeeklyStatsResponse(double totalSwimKm, double totalBikeKm, double totalRunKm,
                                          int totalSessions, int memberCount) {}

    public record ClubRaceGoalResponse(Object goal, boolean hasUpcomingClubSession) {}

    public record ClubActivityResponse(String id, ClubActivityType type, String actorId,
                                       String actorName, String targetId, String targetTitle,
                                       LocalDateTime occurredAt) {}

    public record MyClubRoleEntry(String clubId, String clubName, ClubMemberRole role) {}

    // Endpoints
    @PostMapping
    public ResponseEntity<Club> createClub(@RequestBody CreateClubRequest req) {
        String userId = SecurityUtils.getCurrentUserId();
        return ResponseEntity.ok(clubService.createClub(userId, req));
    }

    @GetMapping
    public ResponseEntity<List<ClubSummaryResponse>> getUserClubs() {
        String userId = SecurityUtils.getCurrentUserId();
        return ResponseEntity.ok(clubService.getUserClubs(userId));
    }

    @GetMapping("/public")
    public ResponseEntity<List<Club>> browsePublicClubs(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(clubService.browsePublicClubs(PageRequest.of(page, size)));
    }

    @GetMapping("/my-roles")
    public ResponseEntity<List<MyClubRoleEntry>> getMyClubRoles() {
        String userId = SecurityUtils.getCurrentUserId();
        return ResponseEntity.ok(clubService.getMyClubRoles(userId));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ClubDetailResponse> getClubDetail(@PathVariable String id) {
        String userId = SecurityUtils.getCurrentUserId();
        return ResponseEntity.ok(clubService.getClubDetail(id, userId));
    }

    @PostMapping("/{id}/join")
    public ResponseEntity<ClubMembership> joinClub(@PathVariable String id) {
        String userId = SecurityUtils.getCurrentUserId();
        return ResponseEntity.ok(clubService.joinClub(userId, id));
    }

    @DeleteMapping("/{id}/leave")
    public ResponseEntity<Void> leaveClub(@PathVariable String id) {
        String userId = SecurityUtils.getCurrentUserId();
        clubService.leaveClub(userId, id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{id}/members")
    public ResponseEntity<List<ClubMemberResponse>> getMembers(@PathVariable String id) {
        return ResponseEntity.ok(clubService.getMembers(id));
    }

    @GetMapping("/{id}/members/pending")
    public ResponseEntity<List<ClubMemberResponse>> getPendingRequests(@PathVariable String id) {
        String userId = SecurityUtils.getCurrentUserId();
        return ResponseEntity.ok(clubService.getPendingRequests(userId, id));
    }

    @PostMapping("/{id}/members/{membershipId}/approve")
    public ResponseEntity<ClubMembership> approveRequest(@PathVariable String id,
                                                          @PathVariable String membershipId) {
        String userId = SecurityUtils.getCurrentUserId();
        return ResponseEntity.ok(clubService.approveRequest(userId, membershipId));
    }

    @DeleteMapping("/{id}/members/{membershipId}/reject")
    public ResponseEntity<Void> rejectRequest(@PathVariable String id,
                                               @PathVariable String membershipId) {
        String userId = SecurityUtils.getCurrentUserId();
        clubService.rejectRequest(userId, membershipId);
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/{id}/members/{membershipId}/role")
    public ResponseEntity<ClubMembership> updateMemberRole(@PathVariable String id,
                                                            @PathVariable String membershipId,
                                                            @RequestBody UpdateMemberRoleRequest req) {
        String userId = SecurityUtils.getCurrentUserId();
        return ResponseEntity.ok(clubService.updateMemberRole(userId, id, membershipId, req.role()));
    }

    @PostMapping("/{id}/groups")
    public ResponseEntity<ClubGroup> createGroup(@PathVariable String id,
                                                  @RequestBody CreateGroupRequest req) {
        String userId = SecurityUtils.getCurrentUserId();
        return ResponseEntity.ok(clubService.createGroup(userId, id, req.name()));
    }

    @GetMapping("/{id}/groups")
    public ResponseEntity<List<ClubGroup>> listGroups(@PathVariable String id) {
        return ResponseEntity.ok(clubService.listGroups(id));
    }

    @DeleteMapping("/{id}/groups/{groupId}")
    public ResponseEntity<Void> deleteGroup(@PathVariable String id, @PathVariable String groupId) {
        String userId = SecurityUtils.getCurrentUserId();
        clubService.deleteGroup(userId, id, groupId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/groups/{groupId}/join")
    public ResponseEntity<ClubGroup> joinGroup(@PathVariable String id, @PathVariable String groupId) {
        String userId = SecurityUtils.getCurrentUserId();
        return ResponseEntity.ok(clubService.joinGroupSelf(userId, id, groupId));
    }

    @DeleteMapping("/{id}/groups/{groupId}/leave")
    public ResponseEntity<Void> leaveGroup(@PathVariable String id, @PathVariable String groupId) {
        String userId = SecurityUtils.getCurrentUserId();
        clubService.leaveGroupSelf(userId, id, groupId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/groups/{groupId}/members/{targetUserId}")
    public ResponseEntity<ClubGroup> addMemberToGroup(@PathVariable String id,
                                                       @PathVariable String groupId,
                                                       @PathVariable String targetUserId) {
        String userId = SecurityUtils.getCurrentUserId();
        return ResponseEntity.ok(clubService.addMemberToGroup(userId, id, groupId, targetUserId));
    }

    @DeleteMapping("/{id}/groups/{groupId}/members/{targetUserId}")
    public ResponseEntity<ClubGroup> removeMemberFromGroup(@PathVariable String id,
                                                            @PathVariable String groupId,
                                                            @PathVariable String targetUserId) {
        String userId = SecurityUtils.getCurrentUserId();
        return ResponseEntity.ok(clubService.removeMemberFromGroup(userId, id, groupId, targetUserId));
    }

    // --- Invite Codes ---

    @PostMapping("/{id}/invite-codes")
    public ResponseEntity<ClubInviteCodeResponse> generateInviteCode(@PathVariable String id,
                                                                      @RequestBody CreateInviteCodeRequest req) {
        String userId = SecurityUtils.getCurrentUserId();
        LocalDateTime expiresAt = req.expiresAt() != null ? LocalDateTime.parse(req.expiresAt()) : null;
        ClubInviteCode code = clubService.generateInviteCode(userId, id, req.clubGroupId(), req.maxUses(), expiresAt);
        return ResponseEntity.ok(toInviteCodeResponse(code));
    }

    @GetMapping("/{id}/invite-codes")
    public ResponseEntity<List<ClubInviteCodeResponse>> getInviteCodes(@PathVariable String id) {
        String userId = SecurityUtils.getCurrentUserId();
        List<ClubInviteCode> codes = clubService.getClubInviteCodes(userId, id);
        List<ClubInviteCodeResponse> responses = codes.stream().map(this::toInviteCodeResponse).toList();
        return ResponseEntity.ok(responses);
    }

    @DeleteMapping("/{id}/invite-codes/{codeId}")
    public ResponseEntity<Void> deactivateInviteCode(@PathVariable String id,
                                                       @PathVariable String codeId) {
        String userId = SecurityUtils.getCurrentUserId();
        clubService.deactivateClubInviteCode(userId, id, codeId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/redeem-invite")
    public ResponseEntity<ClubMembership> redeemInviteCode(@RequestBody RedeemInviteCodeRequest req) {
        String userId = SecurityUtils.getCurrentUserId();
        return ResponseEntity.ok(clubService.redeemClubInviteCode(userId, req.code()));
    }

    private ClubInviteCodeResponse toInviteCodeResponse(ClubInviteCode code) {
        String groupName = null;
        if (code.getClubGroupId() != null) {
            try {
                groupName = clubService.listGroups(code.getClubId()).stream()
                        .filter(g -> g.getId().equals(code.getClubGroupId()))
                        .map(ClubGroup::getName)
                        .findFirst().orElse(null);
            } catch (Exception ignored) {}
        }
        return new ClubInviteCodeResponse(
                code.getId(), code.getCode(), code.getClubId(), code.getCreatedBy(),
                code.getClubGroupId(), groupName,
                code.getMaxUses(), code.getCurrentUses(),
                code.getExpiresAt() != null ? code.getExpiresAt().toString() : null,
                code.isActive(),
                code.getCreatedAt() != null ? code.getCreatedAt().toString() : null);
    }

    @GetMapping("/{id}/feed")
    public ResponseEntity<List<ClubActivityResponse>> getActivityFeed(
            @PathVariable String id,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(clubService.getActivityFeed(id, PageRequest.of(page, size)));
    }

    @PostMapping("/{id}/sessions")
    public ResponseEntity<ClubTrainingSession> createSession(@PathVariable String id,
                                                              @RequestBody CreateSessionRequest req) {
        String userId = SecurityUtils.getCurrentUserId();
        return ResponseEntity.ok(clubService.createSession(userId, id, req));
    }

    @GetMapping("/{id}/sessions")
    public ResponseEntity<List<ClubTrainingSession>> listSessions(
            @PathVariable String id,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime to) {
        if (from != null && to != null) {
            return ResponseEntity.ok(clubService.listSessions(id, from, to));
        }
        return ResponseEntity.ok(clubService.listSessions(id));
    }

    @PostMapping("/{id}/sessions/{sessionId}/join")
    public ResponseEntity<ClubTrainingSession> joinSession(@PathVariable String id,
                                                            @PathVariable String sessionId) {
        String userId = SecurityUtils.getCurrentUserId();
        return ResponseEntity.ok(clubService.joinSession(userId, sessionId));
    }

    @DeleteMapping("/{id}/sessions/{sessionId}/join")
    public ResponseEntity<ClubTrainingSession> cancelSessionParticipation(@PathVariable String id,
                                                                           @PathVariable String sessionId) {
        String userId = SecurityUtils.getCurrentUserId();
        return ResponseEntity.ok(clubService.cancelSessionParticipation(userId, sessionId));
    }

    @GetMapping("/{id}/stats/weekly")
    public ResponseEntity<ClubWeeklyStatsResponse> getWeeklyStats(@PathVariable String id) {
        return ResponseEntity.ok(clubService.getWeeklyStats(id));
    }

    @GetMapping("/{id}/leaderboard")
    public ResponseEntity<List<LeaderboardEntry>> getLeaderboard(@PathVariable String id) {
        return ResponseEntity.ok(clubService.getLeaderboard(id));
    }

    @GetMapping("/{id}/race-goals")
    public ResponseEntity<List<ClubRaceGoalResponse>> getRaceGoals(@PathVariable String id) {
        return ResponseEntity.ok(clubService.getRaceGoals(id));
    }

    // --- Recurring Sessions ---

    @PostMapping("/{id}/recurring-sessions")
    public ResponseEntity<RecurringSessionTemplate> createRecurringSession(
            @PathVariable String id, @RequestBody CreateRecurringSessionRequest req) {
        String userId = SecurityUtils.getCurrentUserId();
        return ResponseEntity.ok(recurringSessionService.createTemplate(userId, id, req));
    }

    @GetMapping("/{id}/recurring-sessions")
    public ResponseEntity<List<RecurringSessionTemplate>> listRecurringSessions(@PathVariable String id) {
        return ResponseEntity.ok(recurringSessionService.listTemplates(id));
    }

    @PutMapping("/{id}/recurring-sessions/{templateId}")
    public ResponseEntity<RecurringSessionTemplate> updateRecurringSession(
            @PathVariable String id, @PathVariable String templateId,
            @RequestBody CreateRecurringSessionRequest req) {
        String userId = SecurityUtils.getCurrentUserId();
        return ResponseEntity.ok(recurringSessionService.updateTemplate(userId, templateId, req));
    }

    @DeleteMapping("/{id}/recurring-sessions/{templateId}")
    public ResponseEntity<Void> deactivateRecurringSession(
            @PathVariable String id, @PathVariable String templateId) {
        String userId = SecurityUtils.getCurrentUserId();
        recurringSessionService.deactivateTemplate(userId, templateId);
        return ResponseEntity.noContent().build();
    }

    // --- Link Training ---

    @PutMapping("/{id}/sessions/{sessionId}/link-training")
    public ResponseEntity<ClubTrainingSession> linkTrainingToSession(
            @PathVariable String id, @PathVariable String sessionId,
            @RequestBody LinkTrainingRequest req) {
        String userId = SecurityUtils.getCurrentUserId();
        return ResponseEntity.ok(clubService.linkTrainingToSession(userId, id, sessionId, req.trainingId()));
    }
}
