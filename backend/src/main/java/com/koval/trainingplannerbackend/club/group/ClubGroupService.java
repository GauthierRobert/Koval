package com.koval.trainingplannerbackend.club.group;

import com.koval.trainingplannerbackend.chat.ChatMembershipService;
import com.koval.trainingplannerbackend.chat.ChatRoom;
import com.koval.trainingplannerbackend.chat.ChatRoomScope;
import com.koval.trainingplannerbackend.chat.ChatRoomService;
import com.koval.trainingplannerbackend.club.invite.ClubInviteCodeService;
import com.koval.trainingplannerbackend.club.membership.ClubAuthorizationService;
import com.koval.trainingplannerbackend.club.membership.ClubMemberStatus;
import com.koval.trainingplannerbackend.club.membership.ClubMembership;
import com.koval.trainingplannerbackend.club.membership.ClubMembershipRepository;
import com.koval.trainingplannerbackend.config.exceptions.ResourceNotFoundException;
import com.koval.trainingplannerbackend.config.exceptions.ValidationException;
import org.springframework.stereotype.Service;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Service
public class ClubGroupService {

    private final ClubGroupRepository clubGroupRepository;
    private final ClubMembershipRepository membershipRepository;
    private final ClubAuthorizationService authorizationService;
    private final ClubInviteCodeService inviteCodeService;
    private final ChatRoomService chatRoomService;
    private final ChatMembershipService chatMembershipService;

    public ClubGroupService(ClubGroupRepository clubGroupRepository,
                            ClubMembershipRepository membershipRepository,
                            ClubAuthorizationService authorizationService,
                            ClubInviteCodeService inviteCodeService,
                            ChatRoomService chatRoomService,
                            ChatMembershipService chatMembershipService) {
        this.clubGroupRepository = clubGroupRepository;
        this.membershipRepository = membershipRepository;
        this.authorizationService = authorizationService;
        this.inviteCodeService = inviteCodeService;
        this.chatRoomService = chatRoomService;
        this.chatMembershipService = chatMembershipService;
    }

    public ClubGroup createGroup(String adminId, String clubId, String name) {
        authorizationService.requireAdminOrOwner(adminId, clubId);
        if (clubGroupRepository.findByClubIdAndName(clubId, name).isPresent()) {
            throw new ValidationException("Group with this name already exists in the club");
        }
        ClubGroup group = new ClubGroup();
        group.setClubId(clubId);
        group.setName(name);
        group.setCreatedAt(java.time.LocalDateTime.now());
        group = clubGroupRepository.save(group);

        inviteCodeService.generateInviteCode(adminId, clubId, group.getId(), 0, null);

        // Provision the group's chat room (empty initially — members are added as they join the group).
        chatRoomService.getOrCreateGroupRoom(clubId, group.getId());

        return group;
    }

    public List<ClubGroup> listGroups(String userId, String clubId) {
        authorizationService.requireActiveMember(userId, clubId);
        return clubGroupRepository.findByClubId(clubId);
    }

    public void deleteGroup(String adminId, String clubId, String groupId) {
        authorizationService.requireAdminOrOwner(adminId, clubId);
        ClubGroup group = requireGroupInClub(clubId, groupId);
        clubGroupRepository.delete(group);
        // Archive the group chat room so it disappears from sidebars but messages are preserved.
        chatRoomService.archiveByParent(ChatRoomScope.GROUP, clubId, groupId);
    }

    public ClubGroup addMemberToGroup(String adminId, String clubId, String groupId, String targetUserId) {
        authorizationService.requireAdminOrOwner(adminId, clubId);
        ClubGroup group = requireGroupInClub(clubId, groupId);
        ClubMembership targetMembership = membershipRepository.findByClubIdAndUserId(clubId, targetUserId)
                .orElseThrow(() -> new ResourceNotFoundException("User is not a member of this club"));
        if (targetMembership.getStatus() != ClubMemberStatus.ACTIVE) {
            throw new ValidationException("User must be an active member to be added to a group");
        }
        if (!group.getMemberIds().contains(targetUserId)) {
            group.getMemberIds().add(targetUserId);
            clubGroupRepository.save(group);
        }
        syncGroupChatMembers(clubId, group);
        return group;
    }

    public ClubGroup removeMemberFromGroup(String adminId, String clubId, String groupId, String targetUserId) {
        authorizationService.requireAdminOrOwner(adminId, clubId);
        ClubGroup group = requireGroupInClub(clubId, groupId);
        group.getMemberIds().remove(targetUserId);
        clubGroupRepository.save(group);
        syncGroupChatMembers(clubId, group);
        return group;
    }

    public ClubGroup joinGroupSelf(String userId, String clubId, String groupId) {
        authorizationService.requireActiveMember(userId, clubId);
        ClubGroup group = requireGroupInClub(clubId, groupId);
        if (!group.getMemberIds().contains(userId)) {
            group.getMemberIds().add(userId);
            clubGroupRepository.save(group);
        }
        syncGroupChatMembers(clubId, group);
        return group;
    }

    public ClubGroup leaveGroupSelf(String userId, String clubId, String groupId) {
        authorizationService.requireActiveMember(userId, clubId);
        ClubGroup group = requireGroupInClub(clubId, groupId);
        group.getMemberIds().remove(userId);
        clubGroupRepository.save(group);
        syncGroupChatMembers(clubId, group);
        return group;
    }

    /**
     * Push the group's memberIds to the chat room, creating it lazily if needed.
     * Reconciles AUTO members only — SELF_JOINED is not applicable for GROUP scope.
     */
    private void syncGroupChatMembers(String clubId, ClubGroup group) {
        ChatRoom room = chatRoomService.getOrCreateGroupRoom(clubId, group.getId());
        Set<String> expected = new HashSet<>(group.getMemberIds());
        chatMembershipService.syncAutoMembers(room.getId(), expected);
    }

    private ClubGroup requireGroupInClub(String clubId, String groupId) {
        ClubGroup group = clubGroupRepository.findById(groupId)
                .orElseThrow(() -> new ResourceNotFoundException("Group not found"));
        if (!group.getClubId().equals(clubId)) {
            throw new ValidationException("Group does not belong to this club");
        }
        return group;
    }
}
