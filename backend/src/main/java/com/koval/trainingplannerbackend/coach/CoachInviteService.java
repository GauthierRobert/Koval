package com.koval.trainingplannerbackend.coach;

import com.koval.trainingplannerbackend.auth.User;
import com.koval.trainingplannerbackend.auth.UserRepository;
import com.koval.trainingplannerbackend.auth.UserRole;
import com.koval.trainingplannerbackend.auth.UserService;
import com.koval.trainingplannerbackend.club.invite.ClubInviteCode;
import com.koval.trainingplannerbackend.club.invite.ClubInviteCodeRepository;
import com.koval.trainingplannerbackend.club.invite.ClubInviteCodeService;
import com.koval.trainingplannerbackend.config.exceptions.ForbiddenOperationException;
import com.koval.trainingplannerbackend.config.exceptions.ResourceNotFoundException;
import com.koval.trainingplannerbackend.config.exceptions.ValidationException;
import com.koval.trainingplannerbackend.training.group.GroupService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Service
public class CoachInviteService {

    private static final String CODE_CHARS = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    private static final int CODE_LENGTH = 8;
    private static final SecureRandom RANDOM = new SecureRandom();

    private final UserRepository userRepository;
    private final UserService userService;
    private final InviteCodeRepository inviteCodeRepository;
    private final ClubInviteCodeRepository clubInviteCodeRepository;
    private final ClubInviteCodeService clubInviteCodeService;
    private final GroupService groupService;

    public CoachInviteService(UserRepository userRepository,
            UserService userService,
            InviteCodeRepository inviteCodeRepository,
            ClubInviteCodeRepository clubInviteCodeRepository,
            ClubInviteCodeService clubInviteCodeService,
            GroupService groupService) {
        this.userRepository = userRepository;
        this.userService = userService;
        this.inviteCodeRepository = inviteCodeRepository;
        this.clubInviteCodeRepository = clubInviteCodeRepository;
        this.clubInviteCodeService = clubInviteCodeService;
        this.groupService = groupService;
    }

    public enum RedeemResult { GROUP, CLUB }

    /**
     * Redeem an invite code that may belong to either a coach (Group) or a club.
     * Returns which kind of code was redeemed.
     */
    @Transactional
    public RedeemResult redeemAnyInviteCode(String userId, String rawCode) {
        String code = rawCode == null ? "" : rawCode.toUpperCase().trim();

        Optional<InviteCode> coachCode = inviteCodeRepository.findByCode(code);
        if (coachCode.isPresent()) {
            redeemInviteCode(userId, code);
            return RedeemResult.GROUP;
        }

        Optional<ClubInviteCode> clubCode = clubInviteCodeRepository.findByCode(code);
        if (clubCode.isPresent()) {
            clubInviteCodeService.redeemClubInviteCode(userId, code);
            return RedeemResult.CLUB;
        }

        throw new ValidationException("Invalid invite code");
    }

    /**
     * Generate an invite code for a coach. Groups param contains Group document IDs.
     */
    public InviteCode generateInviteCode(String coachId, List<String> groupIds, int maxUses, LocalDateTime expiresAt, String customCode) {
        User coach = userRepository.findById(coachId)
                .orElseThrow(() -> new ResourceNotFoundException("User", coachId));

        if (coach.getRole() != UserRole.COACH) {
            throw new ForbiddenOperationException("User is not a coach: " + coachId);
        }

        InviteCode inviteCode = new InviteCode();
        inviteCode.setCode(Optional.ofNullable(customCode)
                .filter(c -> !c.isBlank())
                .map(c -> c.toUpperCase().trim())
                .orElseGet(this::generateUniqueCode));
        inviteCode.setCoachId(coachId);
        inviteCode.setGroupIds(Optional.ofNullable(groupIds).orElseGet(ArrayList::new));
        inviteCode.setMaxUses(maxUses);
        inviteCode.setExpiresAt(expiresAt);
        inviteCode.setType("GROUP");

        return inviteCodeRepository.save(inviteCode);
    }

    /**
     * Redeem an invite code as an athlete.
     * Multi-coach is now allowed — no "already has a coach" check.
     */
    @Transactional
    public User redeemInviteCode(String athleteId, String code) {
        User athlete = userService.getUserById(athleteId);

        InviteCode inviteCode = inviteCodeRepository.findByCode(code.toUpperCase().trim())
                .orElseThrow(() -> new ResourceNotFoundException("Invalid invite code"));

        if (!Boolean.TRUE.equals(inviteCode.getActive())) {
            throw new ValidationException("Invite code is no longer active");
        }

        if (inviteCode.getExpiresAt() != null && LocalDateTime.now().isAfter(inviteCode.getExpiresAt())) {
            throw new ValidationException("Invite code has expired");
        }

        if (inviteCode.getMaxUses() > 0 && inviteCode.getCurrentUses() >= inviteCode.getMaxUses()) {
            throw new ValidationException("Invite code has reached maximum uses");
        }

        // Add athlete to each Group referenced by the invite code
        inviteCode.getGroupIds().forEach(groupId -> groupService.addAthleteToGroup(groupId, athleteId));

        // Increment usage
        inviteCode.setCurrentUses(inviteCode.getCurrentUses() + 1);
        inviteCodeRepository.save(inviteCode);

        return userRepository.findById(athleteId).orElse(athlete);
    }

    /**
     * Get all invite codes for a coach.
     */
    public List<InviteCode> getInviteCodes(String coachId) {
        return inviteCodeRepository.findByCoachId(coachId);
    }

    /**
     * Deactivate an invite code.
     */
    public void deactivateInviteCode(String coachId, String inviteCodeId) {
        InviteCode inviteCode = inviteCodeRepository.findById(inviteCodeId)
                .orElseThrow(() -> new ResourceNotFoundException("Invite code", inviteCodeId));

        if (!coachId.equals(inviteCode.getCoachId())) {
            throw new ForbiddenOperationException("Invite code does not belong to this coach");
        }

        inviteCode.setActive(false);
        inviteCodeRepository.save(inviteCode);
    }

    private String generateUniqueCode() {
        for (int attempt = 0; attempt < 10; attempt++) {
            StringBuilder sb = new StringBuilder(CODE_LENGTH);
            for (int i = 0; i < CODE_LENGTH; i++) {
                sb.append(CODE_CHARS.charAt(RANDOM.nextInt(CODE_CHARS.length())));
            }
            String code = sb.toString();
            if (inviteCodeRepository.findByCode(code).isEmpty()) {
                return code;
            }
        }
        throw new ValidationException("Unable to generate unique invite code after 10 attempts");
    }
}
