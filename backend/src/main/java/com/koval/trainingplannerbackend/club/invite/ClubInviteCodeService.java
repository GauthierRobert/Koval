package com.koval.trainingplannerbackend.club.invite;

import com.koval.trainingplannerbackend.club.Club;
import com.koval.trainingplannerbackend.club.ClubRepository;
import com.koval.trainingplannerbackend.club.activity.ClubActivityService;
import com.koval.trainingplannerbackend.club.activity.ClubActivityType;
import com.koval.trainingplannerbackend.club.dto.ClubInviteCodeResponse;
import com.koval.trainingplannerbackend.club.group.ClubGroup;
import com.koval.trainingplannerbackend.club.group.ClubGroupRepository;
import com.koval.trainingplannerbackend.club.membership.ClubAuthorizationService;
import com.koval.trainingplannerbackend.club.membership.ClubMemberRole;
import com.koval.trainingplannerbackend.club.membership.ClubMemberStatus;
import com.koval.trainingplannerbackend.club.membership.ClubMembership;
import com.koval.trainingplannerbackend.club.membership.ClubMembershipRepository;
import com.koval.trainingplannerbackend.config.exceptions.ResourceNotFoundException;
import com.koval.trainingplannerbackend.config.exceptions.ValidationException;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

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
    private final CacheManager cacheManager;

    public ClubInviteCodeService(ClubInviteCodeRepository clubInviteCodeRepository,
                                 ClubGroupRepository clubGroupRepository,
                                 ClubMembershipRepository membershipRepository,
                                 ClubRepository clubRepository,
                                 ClubAuthorizationService authorizationService,
                                 ClubActivityService activityService,
                                 CacheManager cacheManager) {
        this.clubInviteCodeRepository = clubInviteCodeRepository;
        this.clubGroupRepository = clubGroupRepository;
        this.membershipRepository = membershipRepository;
        this.clubRepository = clubRepository;
        this.authorizationService = authorizationService;
        this.activityService = activityService;
        this.cacheManager = cacheManager;
    }

    @CacheEvict(value = "clubInviteCodes", key = "#clubId")
    public ClubInviteCode generateInviteCode(String userId, String clubId, String clubGroupId,
                                              int maxUses, LocalDateTime expiresAt) {
        authorizationService.requireAdminOrCoach(userId, clubId);

        if (clubGroupId != null && !clubGroupId.isBlank()) {
            ClubGroup group = clubGroupRepository.findById(clubGroupId)
                    .orElseThrow(() -> new ResourceNotFoundException("Club group not found"));
            if (!group.getClubId().equals(clubId)) {
                throw new ValidationException("Group does not belong to this club");
            }
        }

        ClubInviteCode inviteCode = new ClubInviteCode();
        inviteCode.setCode(generateUniqueClubCode());
        inviteCode.setClubId(clubId);
        inviteCode.setCreatedBy(userId);
        inviteCode.setClubGroupId(clubGroupId != null && !clubGroupId.isBlank() ? clubGroupId : null);
        inviteCode.setMaxUses(maxUses);
        inviteCode.setExpiresAt(expiresAt);
        inviteCode.setType(InviteCodeType.CLUB);
        inviteCode.setCreatedAt(LocalDateTime.now());

        return clubInviteCodeRepository.save(inviteCode);
    }

    @CacheEvict(value = "userClubs", key = "#userId")
    @Transactional
    public ClubMembership redeemClubInviteCode(String userId, String code) {
        ClubInviteCode inviteCode = clubInviteCodeRepository.findByCode(code.toUpperCase().trim())
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

        String clubId = inviteCode.getClubId();

        Optional<ClubMembership> existing = membershipRepository.findByClubIdAndUserId(clubId, userId);
        ClubMembership membership;
        if (existing.isPresent()) {
            membership = existing.get();
            if (membership.getStatus() == ClubMemberStatus.PENDING) {
                membership.setStatus(ClubMemberStatus.ACTIVE);
                membership.setJoinedAt(LocalDateTime.now());
                membershipRepository.save(membership);
                incrementMemberCountAndEmitJoined(clubId, userId);
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
            incrementMemberCountAndEmitJoined(clubId, userId);
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

        evictInviteCodesCache(clubId);
        return membership;
    }

    private void evictInviteCodesCache(String clubId) {
        Cache cache = cacheManager.getCache("clubInviteCodes");
        if (cache != null) {
            cache.evict(clubId);
        }
    }

    public List<ClubInviteCode> getClubInviteCodes(String userId, String clubId) {
        authorizationService.requireAdminOrCoach(userId, clubId);
        return clubInviteCodeRepository.findByClubId(clubId);
    }

    @Cacheable(value = "clubInviteCodes", key = "#clubId")
    public List<ClubInviteCodeResponse> getClubInviteCodeResponses(String userId, String clubId) {
        List<ClubInviteCode> codes = getClubInviteCodes(userId, clubId);

        // Batch-load group names
        List<String> groupIds = codes.stream()
                .map(ClubInviteCode::getClubGroupId)
                .filter(gid -> gid != null)
                .distinct()
                .toList();
        Map<String, String> groupNameMap = clubGroupRepository.findAllById(groupIds).stream()
                .collect(Collectors.toMap(ClubGroup::getId, ClubGroup::getName));

        return codes.stream().map(code -> toResponse(code, groupNameMap)).toList();
    }

    public ClubInviteCodeResponse toResponse(ClubInviteCode code) {
        String groupName = null;
        if (code.getClubGroupId() != null) {
            groupName = clubGroupRepository.findById(code.getClubGroupId())
                    .map(ClubGroup::getName).orElse(null);
        }
        return toResponse(code, groupName);
    }

    @CacheEvict(value = "clubInviteCodes", key = "#clubId")
    public void deactivateClubInviteCode(String userId, String clubId, String codeId) {
        authorizationService.requireAdminOrCoach(userId, clubId);
        ClubInviteCode inviteCode = clubInviteCodeRepository.findById(codeId)
                .orElseThrow(() -> new ResourceNotFoundException("Invite code not found"));
        if (!inviteCode.getClubId().equals(clubId)) {
            throw new ValidationException("Invite code does not belong to this club");
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
        throw new ValidationException("Unable to generate unique invite code after 10 attempts");
    }

    private void incrementMemberCountAndEmitJoined(String clubId, String userId) {
        Club club = clubRepository.findById(clubId)
                .orElseThrow(() -> new ResourceNotFoundException("Club not found"));
        club.setMemberCount(club.getMemberCount() + 1);
        clubRepository.save(club);
        activityService.emitActivity(clubId, ClubActivityType.MEMBER_JOINED, userId, null, null);
    }

    private ClubInviteCodeResponse toResponse(ClubInviteCode code, Map<String, String> groupNameMap) {
        return toResponse(code, Optional.ofNullable(code.getClubGroupId()).map(groupNameMap::get).orElse(null));
    }

    private static ClubInviteCodeResponse toResponse(ClubInviteCode code, String groupName) {
        return new ClubInviteCodeResponse(
                code.getId(), code.getCode(), code.getClubId(), code.getCreatedBy(),
                code.getClubGroupId(), groupName,
                code.getMaxUses(), code.getCurrentUses(),
                Optional.ofNullable(code.getExpiresAt()).map(LocalDateTime::toString).orElse(null),
                Boolean.TRUE.equals(code.getActive()),
                Optional.ofNullable(code.getCreatedAt()).map(LocalDateTime::toString).orElse(null));
    }
}
