package com.koval.trainingplannerbackend.club;

import com.koval.trainingplannerbackend.auth.SecurityUtils;
import com.koval.trainingplannerbackend.club.activity.ClubActivityService;
import com.koval.trainingplannerbackend.club.dto.CancelSessionRequest;
import com.koval.trainingplannerbackend.club.dto.ClubDetailResponse;
import com.koval.trainingplannerbackend.club.dto.ClubInviteCodeResponse;
import com.koval.trainingplannerbackend.club.dto.ClubMemberResponse;
import com.koval.trainingplannerbackend.club.dto.ClubRaceGoalResponse;
import com.koval.trainingplannerbackend.club.dto.ClubSummaryResponse;
import com.koval.trainingplannerbackend.club.dto.ClubWeeklyStatsResponse;
import com.koval.trainingplannerbackend.club.dto.CreateClubRequest;
import com.koval.trainingplannerbackend.club.dto.CreateGroupRequest;
import com.koval.trainingplannerbackend.club.dto.CreateInviteCodeRequest;
import com.koval.trainingplannerbackend.club.dto.CreateRecurringSessionRequest;
import com.koval.trainingplannerbackend.club.dto.CreateSessionRequest;
import com.koval.trainingplannerbackend.club.dto.LeaderboardEntry;
import com.koval.trainingplannerbackend.club.dto.LinkTrainingRequest;
import com.koval.trainingplannerbackend.club.dto.MyClubRoleEntry;
import com.koval.trainingplannerbackend.club.dto.RedeemInviteCodeRequest;
import com.koval.trainingplannerbackend.club.dto.UnlinkTrainingRequest;
import com.koval.trainingplannerbackend.club.dto.UpdateMemberRoleRequest;
import com.koval.trainingplannerbackend.club.group.ClubGroup;
import com.koval.trainingplannerbackend.club.group.ClubGroupService;
import com.koval.trainingplannerbackend.club.invite.ClubInviteCode;
import com.koval.trainingplannerbackend.club.invite.ClubInviteCodeService;
import com.koval.trainingplannerbackend.club.membership.ClubMembership;
import com.koval.trainingplannerbackend.club.membership.ClubMembershipService;
import com.koval.trainingplannerbackend.club.recurring.RecurringSessionService;
import com.koval.trainingplannerbackend.club.recurring.RecurringSessionTemplate;
import com.koval.trainingplannerbackend.club.session.ClubSessionService;
import com.koval.trainingplannerbackend.club.session.ClubTrainingSession;
import com.koval.trainingplannerbackend.club.session.SessionCategory;
import com.koval.trainingplannerbackend.club.stats.ClubStatsService;
import org.springframework.data.domain.PageRequest;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.List;

@RestController
@RequestMapping("/api/clubs")
public class ClubController {

    private final ClubService clubService;
    private final ClubSessionService clubSessionService;
    private final ClubMembershipService clubMembershipService;
    private final ClubGroupService clubGroupService;
    private final ClubInviteCodeService clubInviteCodeService;
    private final ClubActivityService clubActivityService;
    private final ClubStatsService clubStatsService;
    private final RecurringSessionService recurringSessionService;

    public ClubController(ClubService clubService, ClubSessionService clubSessionService,
                          ClubMembershipService clubMembershipService,
                          ClubGroupService clubGroupService,
                          ClubInviteCodeService clubInviteCodeService,
                          ClubActivityService clubActivityService,
                          ClubStatsService clubStatsService,
                          RecurringSessionService recurringSessionService) {
        this.clubService = clubService;
        this.clubSessionService = clubSessionService;
        this.clubMembershipService = clubMembershipService;
        this.clubGroupService = clubGroupService;
        this.clubInviteCodeService = clubInviteCodeService;
        this.clubActivityService = clubActivityService;
        this.clubStatsService = clubStatsService;
        this.recurringSessionService = recurringSessionService;
    }

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
        return ResponseEntity.ok(clubMembershipService.getMyClubRoles(userId));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ClubDetailResponse> getClubDetail(@PathVariable String id) {
        String userId = SecurityUtils.getCurrentUserId();
        return ResponseEntity.ok(clubService.getClubDetail(id, userId));
    }

    @PostMapping("/{id}/join")
    public ResponseEntity<ClubMembership> joinClub(@PathVariable String id) {
        String userId = SecurityUtils.getCurrentUserId();
        return ResponseEntity.ok(clubMembershipService.joinClub(userId, id));
    }

    @DeleteMapping("/{id}/leave")
    public ResponseEntity<Void> leaveClub(@PathVariable String id) {
        String userId = SecurityUtils.getCurrentUserId();
        clubMembershipService.leaveClub(userId, id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{id}/members")
    public ResponseEntity<List<ClubMemberResponse>> getMembers(@PathVariable String id) {
        String userId = SecurityUtils.getCurrentUserId();
        return ResponseEntity.ok(clubMembershipService.getMembers(userId, id));
    }

    @GetMapping("/{id}/members/pending")
    public ResponseEntity<List<ClubMemberResponse>> getPendingRequests(@PathVariable String id) {
        String userId = SecurityUtils.getCurrentUserId();
        return ResponseEntity.ok(clubMembershipService.getPendingRequests(userId, id));
    }

    @PostMapping("/{id}/members/{membershipId}/approve")
    public ResponseEntity<ClubMembership> approveRequest(@PathVariable String id,
                                                          @PathVariable String membershipId) {
        String userId = SecurityUtils.getCurrentUserId();
        return ResponseEntity.ok(clubMembershipService.approveRequest(userId, membershipId));
    }

    @DeleteMapping("/{id}/members/{membershipId}/reject")
    public ResponseEntity<Void> rejectRequest(@PathVariable String id,
                                               @PathVariable String membershipId) {
        String userId = SecurityUtils.getCurrentUserId();
        clubMembershipService.rejectRequest(userId, membershipId);
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/{id}/members/{membershipId}/role")
    public ResponseEntity<ClubMembership> updateMemberRole(@PathVariable String id,
                                                            @PathVariable String membershipId,
                                                            @RequestBody UpdateMemberRoleRequest req) {
        String userId = SecurityUtils.getCurrentUserId();
        return ResponseEntity.ok(clubMembershipService.updateMemberRole(userId, id, membershipId, req.role()));
    }

    @PostMapping("/{id}/groups")
    public ResponseEntity<ClubGroup> createGroup(@PathVariable String id,
                                                  @RequestBody CreateGroupRequest req) {
        String userId = SecurityUtils.getCurrentUserId();
        return ResponseEntity.ok(clubGroupService.createGroup(userId, id, req.name()));
    }

    @GetMapping("/{id}/groups")
    public ResponseEntity<List<ClubGroup>> listGroups(@PathVariable String id) {
        String userId = SecurityUtils.getCurrentUserId();
        return ResponseEntity.ok(clubGroupService.listGroups(userId, id));
    }

    @DeleteMapping("/{id}/groups/{groupId}")
    public ResponseEntity<Void> deleteGroup(@PathVariable String id, @PathVariable String groupId) {
        String userId = SecurityUtils.getCurrentUserId();
        clubGroupService.deleteGroup(userId, id, groupId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/groups/{groupId}/join")
    public ResponseEntity<ClubGroup> joinGroup(@PathVariable String id, @PathVariable String groupId) {
        String userId = SecurityUtils.getCurrentUserId();
        return ResponseEntity.ok(clubGroupService.joinGroupSelf(userId, id, groupId));
    }

    @DeleteMapping("/{id}/groups/{groupId}/leave")
    public ResponseEntity<Void> leaveGroup(@PathVariable String id, @PathVariable String groupId) {
        String userId = SecurityUtils.getCurrentUserId();
        clubGroupService.leaveGroupSelf(userId, id, groupId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/groups/{groupId}/members/{targetUserId}")
    public ResponseEntity<ClubGroup> addMemberToGroup(@PathVariable String id,
                                                       @PathVariable String groupId,
                                                       @PathVariable String targetUserId) {
        String userId = SecurityUtils.getCurrentUserId();
        return ResponseEntity.ok(clubGroupService.addMemberToGroup(userId, id, groupId, targetUserId));
    }

    @DeleteMapping("/{id}/groups/{groupId}/members/{targetUserId}")
    public ResponseEntity<ClubGroup> removeMemberFromGroup(@PathVariable String id,
                                                            @PathVariable String groupId,
                                                            @PathVariable String targetUserId) {
        String userId = SecurityUtils.getCurrentUserId();
        return ResponseEntity.ok(clubGroupService.removeMemberFromGroup(userId, id, groupId, targetUserId));
    }

    // --- Invite Codes ---

    @PostMapping("/{id}/invite-codes")
    public ResponseEntity<ClubInviteCodeResponse> generateInviteCode(@PathVariable String id,
                                                                      @RequestBody CreateInviteCodeRequest req) {
        String userId = SecurityUtils.getCurrentUserId();
        LocalDateTime expiresAt = req.expiresAt() != null ? LocalDateTime.parse(req.expiresAt()) : null;
        ClubInviteCode code = clubInviteCodeService.generateInviteCode(userId, id, req.clubGroupId(), req.maxUses(), expiresAt);
        return ResponseEntity.ok(clubInviteCodeService.toResponse(code));
    }

    @GetMapping("/{id}/invite-codes")
    public ResponseEntity<List<ClubInviteCodeResponse>> getInviteCodes(@PathVariable String id) {
        String userId = SecurityUtils.getCurrentUserId();
        return ResponseEntity.ok(clubInviteCodeService.getClubInviteCodeResponses(userId, id));
    }

    @DeleteMapping("/{id}/invite-codes/{codeId}")
    public ResponseEntity<Void> deactivateInviteCode(@PathVariable String id,
                                                       @PathVariable String codeId) {
        String userId = SecurityUtils.getCurrentUserId();
        clubInviteCodeService.deactivateClubInviteCode(userId, id, codeId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/redeem-invite")
    public ResponseEntity<ClubMembership> redeemInviteCode(@RequestBody RedeemInviteCodeRequest req) {
        String userId = SecurityUtils.getCurrentUserId();
        return ResponseEntity.ok(clubInviteCodeService.redeemClubInviteCode(userId, req.code()));
    }

    // Feed endpoint moved to ClubFeedController at /api/clubs/{clubId}/feed

    @PostMapping("/{id}/sessions")
    public ResponseEntity<ClubTrainingSession> createSession(@PathVariable String id,
                                                              @RequestBody CreateSessionRequest req) {
        String userId = SecurityUtils.getCurrentUserId();
        return ResponseEntity.ok(clubSessionService.createSession(userId, id, req));
    }

    @GetMapping("/{id}/sessions")
    public ResponseEntity<List<ClubTrainingSession>> listSessions(
            @PathVariable String id,
            @RequestParam(required = false) SessionCategory category,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime to) {
        String userId = SecurityUtils.getCurrentUserId();
        if (from != null && to != null) {
            return ResponseEntity.ok(clubSessionService.listSessions(userId, id, category, from, to));
        }
        return ResponseEntity.ok(clubSessionService.listSessions(userId, id, category));
    }

    @PutMapping("/{id}/sessions/{sessionId}")
    public ResponseEntity<ClubTrainingSession> updateSession(@PathVariable String id,
                                                              @PathVariable String sessionId,
                                                              @RequestBody CreateSessionRequest req) {
        String userId = SecurityUtils.getCurrentUserId();
        return ResponseEntity.ok(clubSessionService.updateSession(userId, id, sessionId, req));
    }

    @PutMapping("/{id}/sessions/{sessionId}/cancel")
    public ResponseEntity<ClubTrainingSession> cancelEntireSession(@PathVariable String id,
                                                                     @PathVariable String sessionId,
                                                                     @RequestBody CancelSessionRequest req) {
        String userId = SecurityUtils.getCurrentUserId();
        return ResponseEntity.ok(clubSessionService.cancelEntireSession(userId, id, sessionId, req.reason()));
    }

    @PostMapping("/{id}/sessions/{sessionId}/join")
    public ResponseEntity<ClubTrainingSession> joinSession(@PathVariable String id,
                                                            @PathVariable String sessionId) {
        String userId = SecurityUtils.getCurrentUserId();
        return ResponseEntity.ok(clubSessionService.joinSession(userId, sessionId));
    }

    @DeleteMapping("/{id}/sessions/{sessionId}/join")
    public ResponseEntity<ClubTrainingSession> cancelSessionParticipation(@PathVariable String id,
                                                                           @PathVariable String sessionId) {
        String userId = SecurityUtils.getCurrentUserId();
        return ResponseEntity.ok(clubSessionService.cancelSessionParticipation(userId, sessionId));
    }

    @GetMapping("/{id}/stats/weekly")
    public ResponseEntity<ClubWeeklyStatsResponse> getWeeklyStats(@PathVariable String id) {
        String userId = SecurityUtils.getCurrentUserId();
        return ResponseEntity.ok(clubStatsService.getWeeklyStats(userId, id));
    }

    @GetMapping("/{id}/leaderboard")
    public ResponseEntity<List<LeaderboardEntry>> getLeaderboard(@PathVariable String id) {
        String userId = SecurityUtils.getCurrentUserId();
        return ResponseEntity.ok(clubStatsService.getLeaderboard(userId, id));
    }

    @GetMapping("/{id}/race-goals")
    public ResponseEntity<List<ClubRaceGoalResponse>> getRaceGoals(@PathVariable String id) {
        String userId = SecurityUtils.getCurrentUserId();
        return ResponseEntity.ok(clubStatsService.getRaceGoals(userId, id));
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
        String userId = SecurityUtils.getCurrentUserId();
        return ResponseEntity.ok(recurringSessionService.listTemplates(userId, id));
    }

    @PutMapping("/{id}/recurring-sessions/{templateId}")
    public ResponseEntity<RecurringSessionTemplate> updateRecurringSession(
            @PathVariable String id, @PathVariable String templateId,
            @RequestBody CreateRecurringSessionRequest req) {
        String userId = SecurityUtils.getCurrentUserId();
        return ResponseEntity.ok(recurringSessionService.updateTemplate(userId, templateId, req));
    }

    @PutMapping("/{id}/recurring-sessions/{templateId}/with-instances")
    public ResponseEntity<RecurringSessionTemplate> updateRecurringSessionWithInstances(
            @PathVariable String id, @PathVariable String templateId,
            @RequestBody CreateRecurringSessionRequest req) {
        String userId = SecurityUtils.getCurrentUserId();
        RecurringSessionTemplate template = recurringSessionService.updateTemplate(userId, templateId, req);
        recurringSessionService.updateFutureInstances(templateId);
        return ResponseEntity.ok(template);
    }

    @PutMapping("/{id}/recurring-sessions/{templateId}/cancel-future")
    public ResponseEntity<java.util.Map<String, Integer>> cancelFutureRecurringSessions(
            @PathVariable String id, @PathVariable String templateId,
            @RequestBody CancelSessionRequest req) {
        String userId = SecurityUtils.getCurrentUserId();
        int cancelledCount = recurringSessionService.cancelFutureInstances(userId, id, templateId, req.reason());
        return ResponseEntity.ok(java.util.Map.of("cancelledCount", cancelledCount));
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
        return ResponseEntity.ok(clubSessionService.linkTrainingToSession(userId, id, sessionId, req.trainingId(), req.clubGroupId()));
    }

    @PutMapping("/{id}/sessions/{sessionId}/unlink-training")
    public ResponseEntity<ClubTrainingSession> unlinkTrainingFromSession(
            @PathVariable String id, @PathVariable String sessionId,
            @RequestBody UnlinkTrainingRequest req) {
        String userId = SecurityUtils.getCurrentUserId();
        return ResponseEntity.ok(clubSessionService.unlinkTrainingFromSession(userId, id, sessionId, req.clubGroupId()));
    }

    // --- GPX ---

    @PostMapping("/{id}/sessions/{sessionId}/gpx")
    public ResponseEntity<ClubTrainingSession> uploadSessionGpx(
            @PathVariable String id, @PathVariable String sessionId,
            @RequestParam("file") org.springframework.web.multipart.MultipartFile file) {
        String userId = SecurityUtils.getCurrentUserId();
        return ResponseEntity.ok(clubSessionService.uploadGpx(userId, id, sessionId, file));
    }

    @DeleteMapping("/{id}/sessions/{sessionId}/gpx")
    public ResponseEntity<ClubTrainingSession> deleteSessionGpx(
            @PathVariable String id, @PathVariable String sessionId) {
        String userId = SecurityUtils.getCurrentUserId();
        return ResponseEntity.ok(clubSessionService.deleteGpx(userId, id, sessionId));
    }

    @GetMapping("/{id}/sessions/{sessionId}/gpx")
    public ResponseEntity<byte[]> downloadSessionGpx(
            @PathVariable String id, @PathVariable String sessionId) {
        String userId = SecurityUtils.getCurrentUserId();
        return clubSessionService.getGpxDownload(userId, id, sessionId);
    }
}
