package com.koval.trainingplannerbackend.coach;

import com.koval.trainingplannerbackend.auth.SecurityUtils;
import com.koval.trainingplannerbackend.club.invite.ClubInviteCode;
import com.koval.trainingplannerbackend.club.invite.ClubInviteCodeRepository;
import com.koval.trainingplannerbackend.club.invite.ClubInviteCodeService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@RestController
@RequestMapping("/api/coach")
@CrossOrigin(origins = "*")
public class CoachInviteController {

    private final CoachInviteService coachInviteService;
    private final InviteCodeRepository inviteCodeRepository;
    private final ClubInviteCodeRepository clubInviteCodeRepository;
    private final ClubInviteCodeService clubInviteCodeService;

    public CoachInviteController(CoachInviteService coachInviteService,
                                 InviteCodeRepository inviteCodeRepository,
                                 ClubInviteCodeRepository clubInviteCodeRepository,
                                 ClubInviteCodeService clubInviteCodeService) {
        this.coachInviteService = coachInviteService;
        this.inviteCodeRepository = inviteCodeRepository;
        this.clubInviteCodeRepository = clubInviteCodeRepository;
        this.clubInviteCodeService = clubInviteCodeService;
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
    public ResponseEntity<?> redeemInviteCode(@RequestBody RedeemRequest request) {
        String userId = SecurityUtils.getCurrentUserId();
        String normalizedCode = request.code().toUpperCase().trim();

        // Look up in coach invite codes
        Optional<InviteCode> coachCode = inviteCodeRepository.findByCode(normalizedCode);
        if (coachCode.isPresent()) {
            coachInviteService.redeemInviteCode(userId, normalizedCode);
            return ResponseEntity.ok(new RedeemResponse("GROUP", "Joined training group"));
        }

        // Look up in club invite codes
        Optional<ClubInviteCode> clubCode = clubInviteCodeRepository.findByCode(normalizedCode);
        if (clubCode.isPresent()) {
            clubInviteCodeService.redeemClubInviteCode(userId, normalizedCode);
            return ResponseEntity.ok(new RedeemResponse("CLUB", "Joined club successfully"));
        }

        throw new com.koval.trainingplannerbackend.config.exceptions.ValidationException("Invalid invite code");
    }
}
