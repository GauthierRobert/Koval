package com.koval.trainingplannerbackend.club;

import com.koval.trainingplannerbackend.auth.SecurityUtils;
import com.koval.trainingplannerbackend.club.dto.ClubInviteCodeResponse;
import com.koval.trainingplannerbackend.club.dto.CreateInviteCodeRequest;
import com.koval.trainingplannerbackend.club.dto.RedeemInviteCodeRequest;
import com.koval.trainingplannerbackend.club.invite.ClubInviteCode;
import com.koval.trainingplannerbackend.club.invite.ClubInviteCodeService;
import com.koval.trainingplannerbackend.club.membership.ClubMembership;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.List;

@RestController
@RequestMapping("/api/clubs")
public class ClubInviteController {

    private final ClubInviteCodeService clubInviteCodeService;

    public ClubInviteController(ClubInviteCodeService clubInviteCodeService) {
        this.clubInviteCodeService = clubInviteCodeService;
    }

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
    public ResponseEntity<ClubMembership> redeemInviteCode(@Valid @RequestBody RedeemInviteCodeRequest req) {
        String userId = SecurityUtils.getCurrentUserId();
        return ResponseEntity.ok(clubInviteCodeService.redeemClubInviteCode(userId, req.code()));
    }
}
