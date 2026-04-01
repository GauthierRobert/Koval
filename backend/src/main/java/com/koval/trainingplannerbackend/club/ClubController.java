package com.koval.trainingplannerbackend.club;

import com.koval.trainingplannerbackend.auth.SecurityUtils;
import com.koval.trainingplannerbackend.club.dto.ClubDetailResponse;
import com.koval.trainingplannerbackend.club.dto.ClubMemberResponse;
import com.koval.trainingplannerbackend.club.dto.ClubSummaryResponse;
import com.koval.trainingplannerbackend.club.dto.CreateClubRequest;
import com.koval.trainingplannerbackend.club.dto.MyClubRoleEntry;
import com.koval.trainingplannerbackend.club.dto.UpdateMemberRoleRequest;
import com.koval.trainingplannerbackend.club.membership.ClubMembership;
import com.koval.trainingplannerbackend.club.membership.ClubMembershipService;
import jakarta.validation.Valid;
import org.springframework.data.domain.PageRequest;
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

import java.util.List;

@RestController
@RequestMapping("/api/clubs")
public class ClubController {

    private final ClubService clubService;
    private final ClubMembershipService clubMembershipService;

    public ClubController(ClubService clubService, ClubMembershipService clubMembershipService) {
        this.clubService = clubService;
        this.clubMembershipService = clubMembershipService;
    }

    // Endpoints
    @PostMapping
    public ResponseEntity<Club> createClub(@Valid @RequestBody CreateClubRequest req) {
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
}
