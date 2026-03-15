package com.koval.trainingplannerbackend.club;

import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Service
public class ClubInviteCodeService {

    private static final String CODE_CHARS = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    private static final int CODE_LENGTH = 8;
    private static final SecureRandom RANDOM = new SecureRandom();

    private final ClubInviteCodeRepository clubInviteCodeRepository;
    private final ClubGroupRepository clubGroupRepository;
    private final ClubMembershipRepository membershipRepository;
    private final ClubRepository clubRepository;
    private final ClubAuthorizationService authorizationService;
    private final ClubActivityService activityService;

    public ClubInviteCodeService(ClubInviteCodeRepository clubInviteCodeRepository,
                                 ClubGroupRepository clubGroupRepository,
                                 ClubMembershipRepository membershipRepository,
                                 ClubRepository clubRepository,
                                 ClubAuthorizationService authorizationService,
                                 ClubActivityService activityService) {
        this.clubInviteCodeRepository = clubInviteCodeRepository;
        this.clubGroupRepository = clubGroupRepository;
        this.membershipRepository = membershipRepository;
        this.clubRepository = clubRepository;
        this.authorizationService = authorizationService;
        this.activityService = activityService;
    }

    public ClubInviteCode generateInviteCode(String userId, String clubId, String clubGroupId,
                                              int maxUses, LocalDateTime expiresAt) {
        authorizationService.requireAdminOrCoach(userId, clubId);

        if (clubGroupId != null && !clubGroupId.isBlank()) {
            ClubGroup group = clubGroupRepository.findById(clubGroupId)
                    .orElseThrow(() -> new IllegalArgumentException("Club group not found"));
            if (!group.getClubId().equals(clubId)) {
                throw new IllegalArgumentException("Group does not belong to this club");
            }
        }

        ClubInviteCode inviteCode = new ClubInviteCode();
        inviteCode.setCode(generateUniqueClubCode());
        inviteCode.setClubId(clubId);
        inviteCode.setCreatedBy(userId);
        inviteCode.setClubGroupId(clubGroupId != null && !clubGroupId.isBlank() ? clubGroupId : null);
        inviteCode.setMaxUses(maxUses);
        inviteCode.setExpiresAt(expiresAt);
        inviteCode.setType("CLUB");
        inviteCode.setCreatedAt(LocalDateTime.now());

        return clubInviteCodeRepository.save(inviteCode);
    }

    public ClubMembership redeemClubInviteCode(String userId, String code) {
        ClubInviteCode inviteCode = clubInviteCodeRepository.findByCode(code.toUpperCase().trim())
                .orElseThrow(() -> new IllegalArgumentException("Invalid invite code"));

        if (!inviteCode.isActive()) {
            throw new IllegalStateException("Invite code is no longer active");
        }
        if (inviteCode.getExpiresAt() != null && LocalDateTime.now().isAfter(inviteCode.getExpiresAt())) {
            throw new IllegalStateException("Invite code has expired");
        }
        if (inviteCode.getMaxUses() > 0 && inviteCode.getCurrentUses() >= inviteCode.getMaxUses()) {
            throw new IllegalStateException("Invite code has reached maximum uses");
        }

        String clubId = inviteCode.getClubId();

        Optional<ClubMembership> existing = membershipRepository.findByClubIdAndUserId(clubId, userId);
        ClubMembership membership;
        if (existing.isPresent()) {
            membership = existing.get();
            if (membership.getStatus() == ClubMemberStatus.PENDING) {
                membership.setStatus(ClubMemberStatus.ACTIVE);
                membership.setJoinedAt(LocalDateTime.now());
                membershipRepository.save(membership);
                Club club = clubRepository.findById(clubId)
                        .orElseThrow(() -> new IllegalArgumentException("Club not found"));
                club.setMemberCount(club.getMemberCount() + 1);
                clubRepository.save(club);
                activityService.emitActivity(clubId, ClubActivityType.MEMBER_JOINED, userId, null, null);
            }
        } else {
            membership = new ClubMembership();
            membership.setClubId(clubId);
            membership.setUserId(userId);
            membership.setRole(ClubMemberRole.MEMBER);
            membership.setStatus(ClubMemberStatus.ACTIVE);
            membership.setJoinedAt(LocalDateTime.now());
            membership.setRequestedAt(LocalDateTime.now());
            membership = membershipRepository.save(membership);

            Club club = clubRepository.findById(clubId)
                    .orElseThrow(() -> new IllegalArgumentException("Club not found"));
            club.setMemberCount(club.getMemberCount() + 1);
            clubRepository.save(club);
            activityService.emitActivity(clubId, ClubActivityType.MEMBER_JOINED, userId, null, null);
        }

        if (inviteCode.getClubGroupId() != null) {
            ClubGroup group = clubGroupRepository.findById(inviteCode.getClubGroupId()).orElse(null);
            if (group != null && !group.getMemberIds().contains(userId)) {
                group.getMemberIds().add(userId);
                clubGroupRepository.save(group);
            }
        }

        inviteCode.setCurrentUses(inviteCode.getCurrentUses() + 1);
        clubInviteCodeRepository.save(inviteCode);

        return membership;
    }

    public List<ClubInviteCode> getClubInviteCodes(String userId, String clubId) {
        authorizationService.requireAdminOrCoach(userId, clubId);
        return clubInviteCodeRepository.findByClubId(clubId);
    }

    public void deactivateClubInviteCode(String userId, String clubId, String codeId) {
        authorizationService.requireAdminOrCoach(userId, clubId);
        ClubInviteCode inviteCode = clubInviteCodeRepository.findById(codeId)
                .orElseThrow(() -> new IllegalArgumentException("Invite code not found"));
        if (!inviteCode.getClubId().equals(clubId)) {
            throw new IllegalArgumentException("Invite code does not belong to this club");
        }
        inviteCode.setActive(false);
        clubInviteCodeRepository.save(inviteCode);
    }

    public ClubInviteCode saveCode(ClubInviteCode code) {
        return clubInviteCodeRepository.save(code);
    }

    public String generateUniqueClubCode() {
        for (int attempt = 0; attempt < 10; attempt++) {
            StringBuilder sb = new StringBuilder(CODE_LENGTH);
            for (int i = 0; i < CODE_LENGTH; i++) {
                sb.append(CODE_CHARS.charAt(RANDOM.nextInt(CODE_CHARS.length())));
            }
            String code = sb.toString();
            if (clubInviteCodeRepository.findByCode(code).isEmpty()) {
                return code;
            }
        }
        throw new IllegalStateException("Unable to generate unique invite code after 10 attempts");
    }
}
