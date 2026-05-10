package com.koval.trainingplannerbackend.coach;

import com.koval.trainingplannerbackend.auth.SecurityUtils;
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
@RequestMapping("/api/coach")
public class CoachInviteController {

    private final CoachInviteService coachInviteService;

    public CoachInviteController(CoachInviteService coachInviteService) {
        this.coachInviteService = coachInviteService;
    }

    public record InviteCodeRequest(List<String> groups, int maxUses, LocalDateTime expiresAt, String code) {}

    public record RedeemRequest(String code) {}

    public record RedeemResponse(String type, String message) {}

    @PostMapping("/invite-codes")
    public ResponseEntity<InviteCode> generateInviteCode(
            @RequestBody InviteCodeRequest request) {
        String coachId = SecurityUtils.getCurrentUserId();
        InviteCode code = coachInviteService.generateInviteCode(
                coachId, request.groups(), request.maxUses(), request.expiresAt(), request.code());
        return ResponseEntity.ok(code);
    }

    @GetMapping("/invite-codes")
    public ResponseEntity<List<InviteCode>> getInviteCodes() {
        String coachId = SecurityUtils.getCurrentUserId();
        return ResponseEntity.ok(coachInviteService.getInviteCodes(coachId));
    }

    @DeleteMapping("/invite-codes/{id}")
    public ResponseEntity<Void> deactivateInviteCode(@PathVariable String id) {
        String coachId = SecurityUtils.getCurrentUserId();
        coachInviteService.deactivateInviteCode(coachId, id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/redeem-invite")
    public ResponseEntity<RedeemResponse> redeemInviteCode(@RequestBody RedeemRequest request) {
        String userId = SecurityUtils.getCurrentUserId();
        CoachInviteService.RedeemResult result = coachInviteService.redeemAnyInviteCode(userId, request.code());
        return switch (result) {
            case GROUP -> ResponseEntity.ok(new RedeemResponse("GROUP", "Joined training group"));
            case CLUB -> ResponseEntity.ok(new RedeemResponse("CLUB", "Joined club successfully"));
        };
    }
}
