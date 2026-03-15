package com.koval.trainingplannerbackend.club;

import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class ClubGroupService {

    private final ClubGroupRepository clubGroupRepository;
    private final ClubMembershipRepository membershipRepository;
    private final ClubInviteCodeRepository clubInviteCodeRepository;
    private final ClubAuthorizationService authorizationService;
    private final ClubInviteCodeService inviteCodeService;

    public ClubGroupService(ClubGroupRepository clubGroupRepository,
                            ClubMembershipRepository membershipRepository,
                            ClubInviteCodeRepository clubInviteCodeRepository,
                            ClubAuthorizationService authorizationService,
                            ClubInviteCodeService inviteCodeService) {
        this.clubGroupRepository = clubGroupRepository;
        this.membershipRepository = membershipRepository;
        this.clubInviteCodeRepository = clubInviteCodeRepository;
        this.authorizationService = authorizationService;
        this.inviteCodeService = inviteCodeService;
    }

    public ClubGroup createGroup(String adminId, String clubId, String name) {
        authorizationService.requireAdminOrOwner(adminId, clubId);
        if (clubGroupRepository.findByClubIdAndName(clubId, name).isPresent()) {
            throw new IllegalStateException("Group with this name already exists in the club");
        }
        ClubGroup group = new ClubGroup();
        group.setClubId(clubId);
        group.setName(name);
        group.setCreatedAt(LocalDateTime.now());
        group = clubGroupRepository.save(group);

        // Auto-generate invite code for the group
        ClubInviteCode autoCode = new ClubInviteCode();
        autoCode.setCode(inviteCodeService.generateUniqueClubCode());
        autoCode.setClubId(clubId);
        autoCode.setCreatedBy(adminId);
        autoCode.setClubGroupId(group.getId());
        autoCode.setMaxUses(0);
        autoCode.setType("CLUB");
        autoCode.setCreatedAt(LocalDateTime.now());
        clubInviteCodeRepository.save(autoCode);

        return group;
    }

    public List<ClubGroup> listGroups(String clubId) {
        return clubGroupRepository.findByClubId(clubId);
    }

    public void deleteGroup(String adminId, String clubId, String groupId) {
        authorizationService.requireAdminOrOwner(adminId, clubId);
        ClubGroup group = clubGroupRepository.findById(groupId)
                .orElseThrow(() -> new IllegalArgumentException("Group not found"));
        if (!group.getClubId().equals(clubId)) {
            throw new IllegalArgumentException("Group does not belong to this club");
        }
        clubGroupRepository.delete(group);
    }

    public ClubGroup addMemberToGroup(String adminId, String clubId, String groupId, String targetUserId) {
        authorizationService.requireAdminOrOwner(adminId, clubId);
        ClubGroup group = clubGroupRepository.findById(groupId)
                .orElseThrow(() -> new IllegalArgumentException("Group not found"));
        if (!group.getClubId().equals(clubId)) {
            throw new IllegalArgumentException("Group does not belong to this club");
        }
        ClubMembership targetMembership = membershipRepository.findByClubIdAndUserId(clubId, targetUserId)
                .orElseThrow(() -> new IllegalArgumentException("User is not a member of this club"));
        if (targetMembership.getStatus() != ClubMemberStatus.ACTIVE) {
            throw new IllegalStateException("User must be an active member to be added to a group");
        }
        if (!group.getMemberIds().contains(targetUserId)) {
            group.getMemberIds().add(targetUserId);
            clubGroupRepository.save(group);
        }
        return group;
    }

    public ClubGroup removeMemberFromGroup(String adminId, String clubId, String groupId, String targetUserId) {
        authorizationService.requireAdminOrOwner(adminId, clubId);
        ClubGroup group = clubGroupRepository.findById(groupId)
                .orElseThrow(() -> new IllegalArgumentException("Group not found"));
        if (!group.getClubId().equals(clubId)) {
            throw new IllegalArgumentException("Group does not belong to this club");
        }
        group.getMemberIds().remove(targetUserId);
        return clubGroupRepository.save(group);
    }

    public ClubGroup joinGroupSelf(String userId, String clubId, String groupId) {
        authorizationService.requireActiveMember(userId, clubId);
        ClubGroup group = clubGroupRepository.findById(groupId)
                .orElseThrow(() -> new IllegalArgumentException("Group not found"));
        if (!group.getClubId().equals(clubId)) {
            throw new IllegalArgumentException("Group does not belong to this club");
        }
        if (!group.getMemberIds().contains(userId)) {
            group.getMemberIds().add(userId);
            clubGroupRepository.save(group);
        }
        return group;
    }

    public ClubGroup leaveGroupSelf(String userId, String clubId, String groupId) {
        ClubGroup group = clubGroupRepository.findById(groupId)
                .orElseThrow(() -> new IllegalArgumentException("Group not found"));
        if (!group.getClubId().equals(clubId)) {
            throw new IllegalArgumentException("Group does not belong to this club");
        }
        group.getMemberIds().remove(userId);
        return clubGroupRepository.save(group);
    }
}
