package com.koval.trainingplannerbackend.club;

import com.koval.trainingplannerbackend.auth.SecurityUtils;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;

@RestController
@RequestMapping("/api/clubs")
public class ClubController {

    private final ClubService clubService;

    public ClubController(ClubService clubService) {
        this.clubService = clubService;
    }

    // DTOs
    public record CreateClubRequest(String name, String description, String location,
                                    String logoUrl, ClubVisibility visibility) {}

    public record CreateSessionRequest(String title, String sport, LocalDateTime scheduledAt,
                                       String location, String description, String linkedTrainingId) {}

    public record CreateTagRequest(String name) {}

    public record UpdateMemberRoleRequest(ClubMemberRole role) {}

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

    @PostMapping("/{id}/tags")
    public ResponseEntity<ClubTag> createTag(@PathVariable String id,
                                              @RequestBody CreateTagRequest req) {
        String userId = SecurityUtils.getCurrentUserId();
        return ResponseEntity.ok(clubService.createTag(userId, id, req.name()));
    }

    @GetMapping("/{id}/tags")
    public ResponseEntity<List<ClubTag>> listTags(@PathVariable String id) {
        return ResponseEntity.ok(clubService.listTags(id));
    }

    @DeleteMapping("/{id}/tags/{tagId}")
    public ResponseEntity<Void> deleteTag(@PathVariable String id, @PathVariable String tagId) {
        String userId = SecurityUtils.getCurrentUserId();
        clubService.deleteTag(userId, id, tagId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/tags/{tagId}/members/{targetUserId}")
    public ResponseEntity<ClubTag> addMemberToTag(@PathVariable String id,
                                                   @PathVariable String tagId,
                                                   @PathVariable String targetUserId) {
        String userId = SecurityUtils.getCurrentUserId();
        return ResponseEntity.ok(clubService.addMemberToTag(userId, id, tagId, targetUserId));
    }

    @DeleteMapping("/{id}/tags/{tagId}/members/{targetUserId}")
    public ResponseEntity<ClubTag> removeMemberFromTag(@PathVariable String id,
                                                        @PathVariable String tagId,
                                                        @PathVariable String targetUserId) {
        String userId = SecurityUtils.getCurrentUserId();
        return ResponseEntity.ok(clubService.removeMemberFromTag(userId, id, tagId, targetUserId));
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
    public ResponseEntity<List<ClubTrainingSession>> listSessions(@PathVariable String id) {
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
}
