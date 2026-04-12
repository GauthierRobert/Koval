package com.koval.trainingplannerbackend.club.membership;

import com.koval.trainingplannerbackend.auth.User;
import com.koval.trainingplannerbackend.auth.UserService;
import com.koval.trainingplannerbackend.chat.ChatMemberRole;
import com.koval.trainingplannerbackend.chat.ChatMembershipService;
import com.koval.trainingplannerbackend.chat.ChatRoom;
import com.koval.trainingplannerbackend.chat.ChatRoomService;
import com.koval.trainingplannerbackend.chat.MembershipSource;
import com.koval.trainingplannerbackend.club.Club;
import com.koval.trainingplannerbackend.club.ClubRepository;
import com.koval.trainingplannerbackend.club.ClubVisibility;
import com.koval.trainingplannerbackend.club.activity.ClubActivityService;
import com.koval.trainingplannerbackend.club.activity.ClubActivityType;
import com.koval.trainingplannerbackend.club.dto.ClubMemberResponse;
import com.koval.trainingplannerbackend.club.dto.MyClubRoleEntry;
import com.koval.trainingplannerbackend.club.group.ClubGroup;
import com.koval.trainingplannerbackend.club.group.ClubGroupRepository;
import com.koval.trainingplannerbackend.config.exceptions.ForbiddenOperationException;
import com.koval.trainingplannerbackend.config.exceptions.ResourceNotFoundException;
import com.koval.trainingplannerbackend.config.exceptions.ValidationException;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
public class ClubMembershipService {

    private final ClubRepository clubRepository;
    private final ClubMembershipRepository membershipRepository;
    private final ClubGroupRepository clubGroupRepository;
    private final UserService userService;
    private final ClubAuthorizationService authorizationService;
    private final ClubActivityService activityService;
    private final ChatRoomService chatRoomService;
    private final ChatMembershipService chatMembershipService;

    public ClubMembershipService(ClubRepository clubRepository,
                                 ClubMembershipRepository membershipRepository,
                                 ClubGroupRepository clubGroupRepository,
                                 UserService userService,
                                 ClubAuthorizationService authorizationService,
                                 ClubActivityService activityService,
                                 ChatRoomService chatRoomService,
                                 ChatMembershipService chatMembershipService) {
        this.clubRepository = clubRepository;
        this.membershipRepository = membershipRepository;
        this.clubGroupRepository = clubGroupRepository;
        this.userService = userService;
        this.authorizationService = authorizationService;
        this.activityService = activityService;
        this.chatRoomService = chatRoomService;
        this.chatMembershipService = chatMembershipService;
    }

    @CacheEvict(value = "userClubs", key = "#userId")
    @Transactional
    public ClubMembership joinClub(String userId, String clubId) {
        Club club = clubRepository.findById(clubId)
                .orElseThrow(() -> new ResourceNotFoundException("Club not found"));

        Optional<ClubMembership> existing = membershipRepository.findByClubIdAndUserId(clubId, userId);
        if (existing.isPresent()) {
            throw new ValidationException("Already a member or pending");
        }

        ClubMembership membership = new ClubMembership();
        membership.setClubId(clubId);
        membership.setUserId(userId);
        membership.setRole(ClubMemberRole.MEMBER);
        membership.setRequestedAt(LocalDateTime.now());

        if (club.getVisibility() == ClubVisibility.PUBLIC) {
            membership.setStatus(ClubMemberStatus.ACTIVE);
            membership.setJoinedAt(LocalDateTime.now());
            club.setMemberCount(club.getMemberCount() + 1);
            clubRepository.save(club);
            activityService.emitActivity(clubId, ClubActivityType.MEMBER_JOINED, userId, null, null);
            // Auto-join the club chat room on immediate activation.
            ChatRoom clubRoom = chatRoomService.getOrCreateClubRoom(clubId);
            chatMembershipService.ensureMembership(clubRoom, userId, MembershipSource.AUTO, ChatMemberRole.MEMBER);
        } else {
            membership.setStatus(ClubMemberStatus.PENDING);
        }
        return membershipRepository.save(membership);
    }

    @CacheEvict(value = "userClubs", key = "#userId")
    public void leaveClub(String userId, String clubId) {
        ClubMembership membership = membershipRepository.findByClubIdAndUserId(clubId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Not a member"));

        if (membership.getRole() == ClubMemberRole.OWNER) {
            throw new ForbiddenOperationException("Owner cannot leave the club");
        }

        if (membership.getStatus() == ClubMemberStatus.ACTIVE) {
            Club club = clubRepository.findById(clubId)
                    .orElseThrow(() -> new ResourceNotFoundException("Club not found"));
            club.setMemberCount(Math.max(0, club.getMemberCount() - 1));
            clubRepository.save(club);
            activityService.emitActivity(clubId, ClubActivityType.MEMBER_LEFT, userId, null, null);
        }
        membershipRepository.delete(membership);
        // Soft-leave every chat room in this club so lastReadAt is preserved on rejoin.
        chatMembershipService.deactivateAllForUserInClub(clubId, userId);
    }

    @CacheEvict(value = "userClubs", key = "#adminId")
    public ClubMembership approveRequest(String adminId, String membershipId) {
        ClubMembership target = membershipRepository.findById(membershipId)
                .orElseThrow(() -> new ResourceNotFoundException("Membership not found"));
        authorizationService.requireAdminOrOwner(adminId, target.getClubId());

        target.setStatus(ClubMemberStatus.ACTIVE);
        target.setJoinedAt(LocalDateTime.now());
        membershipRepository.save(target);

        Club club = clubRepository.findById(target.getClubId())
                .orElseThrow(() -> new ResourceNotFoundException("Club not found"));
        club.setMemberCount(club.getMemberCount() + 1);
        clubRepository.save(club);
        activityService.emitActivity(target.getClubId(), ClubActivityType.MEMBER_JOINED, target.getUserId(), null, null);
        // Add the newly approved member to the club chat room.
        ChatRoom clubRoom = chatRoomService.getOrCreateClubRoom(target.getClubId());
        chatMembershipService.ensureMembership(clubRoom, target.getUserId(), MembershipSource.AUTO, ChatMemberRole.MEMBER);
        return target;
    }

    public void rejectRequest(String adminId, String membershipId) {
        ClubMembership target = membershipRepository.findById(membershipId)
                .orElseThrow(() -> new ResourceNotFoundException("Membership not found"));
        authorizationService.requireAdminOrOwner(adminId, target.getClubId());
        membershipRepository.delete(target);
    }

    public List<ClubMemberResponse> getMembers(String userId, String clubId) {
        authorizationService.requireActiveMember(userId, clubId);
        List<ClubMembership> memberships = membershipRepository.findByClubIdAndStatus(clubId, ClubMemberStatus.ACTIVE);

        List<ClubGroup> allGroups = clubGroupRepository.findByClubId(clubId);
        Map<String, List<String>> userGroupsMap = new HashMap<>();
        for (ClubGroup group : allGroups) {
            for (String memberId : group.getMemberIds()) {
                userGroupsMap.computeIfAbsent(memberId, k -> new ArrayList<>()).add(group.getName());
            }
        }

        // Batch user lookup (N+1 fix)
        List<String> userIds = memberships.stream().map(ClubMembership::getUserId).toList();
        Map<String, User> userMap = userService.findAllById(userIds).stream()
                .collect(Collectors.toMap(User::getId, u -> u));

        return memberships.stream().map(m -> {
            User user = userMap.get(m.getUserId());
            String displayName = user != null ? user.getDisplayName() : m.getUserId();
            String pic = user != null ? user.getProfilePicture() : null;
            List<String> tags = userGroupsMap.getOrDefault(m.getUserId(), List.of());
            return new ClubMemberResponse(
                    m.getId(), m.getUserId(), displayName, pic, m.getRole(), m.getJoinedAt(), tags);
        }).collect(Collectors.toList());
    }

    public List<ClubMemberResponse> getPendingRequests(String adminId, String clubId) {
        authorizationService.requireAdminOrOwner(adminId, clubId);
        List<ClubMembership> pending = membershipRepository.findByClubIdAndStatus(clubId, ClubMemberStatus.PENDING);

        // Batch user lookup (N+1 fix)
        List<String> userIds = pending.stream().map(ClubMembership::getUserId).toList();
        Map<String, User> userMap = userService.findAllById(userIds).stream()
                .collect(Collectors.toMap(User::getId, u -> u));

        return pending.stream().map(m -> {
            User user = userMap.get(m.getUserId());
            String displayName = user != null ? user.getDisplayName() : m.getUserId();
            String pic = user != null ? user.getProfilePicture() : null;
            return new ClubMemberResponse(
                    m.getId(), m.getUserId(), displayName, pic, m.getRole(), m.getRequestedAt(), List.of());
        }).collect(Collectors.toList());
    }

    public ClubMembership updateMemberRole(String callerId, String clubId, String membershipId, ClubMemberRole newRole) {
        ClubMembership caller = membershipRepository.findByClubIdAndUserId(clubId, callerId)
                .orElseThrow(() -> new ResourceNotFoundException("Not a member"));
        if (caller.getRole() != ClubMemberRole.OWNER && caller.getRole() != ClubMemberRole.ADMIN) {
            throw new ForbiddenOperationException("Only owner or admin can change member roles");
        }
        if (newRole == ClubMemberRole.OWNER) {
            throw new ForbiddenOperationException("Cannot promote a member to OWNER");
        }
        if (newRole == ClubMemberRole.ADMIN && caller.getRole() != ClubMemberRole.OWNER) {
            throw new ForbiddenOperationException("Only the owner can promote members to ADMIN");
        }

        ClubMembership target = membershipRepository.findById(membershipId)
                .orElseThrow(() -> new ResourceNotFoundException("Membership not found"));
        if (target.getRole() == ClubMemberRole.OWNER) {
            throw new ForbiddenOperationException("Cannot change the owner's role");
        }
        if (target.getRole() == ClubMemberRole.ADMIN && caller.getRole() != ClubMemberRole.OWNER) {
            throw new ForbiddenOperationException("Only the owner can change an admin's role");
        }
        target.setRole(newRole);
        return membershipRepository.save(target);
    }

    public List<MyClubRoleEntry> getMyClubRoles(String userId) {
        List<ClubMembership> memberships = membershipRepository.findByUserId(userId).stream()
                .filter(m -> m.getStatus() == ClubMemberStatus.ACTIVE)
                .collect(Collectors.toList());
        List<String> clubIds = memberships.stream().map(ClubMembership::getClubId).toList();
        Map<String, Club> clubMap = clubRepository.findAllById(clubIds).stream()
                .collect(Collectors.toMap(Club::getId, c -> c));

        return memberships.stream()
                .filter(m -> clubMap.containsKey(m.getClubId()))
                .map(m -> {
                    Club c = clubMap.get(m.getClubId());
                    return new MyClubRoleEntry(c.getId(), c.getName(), m.getRole());
                })
                .collect(Collectors.toList());
    }

    public List<String> getActiveMemberIds(String clubId) {
        return membershipRepository.findByClubIdAndStatus(clubId, ClubMemberStatus.ACTIVE)
                .stream().map(ClubMembership::getUserId).collect(Collectors.toList());
    }
}
