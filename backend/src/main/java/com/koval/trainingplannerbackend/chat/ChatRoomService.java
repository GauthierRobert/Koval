package com.koval.trainingplannerbackend.chat;

import com.koval.trainingplannerbackend.club.ClubRepository;
import com.koval.trainingplannerbackend.club.group.ClubGroup;
import com.koval.trainingplannerbackend.club.group.ClubGroupRepository;
import com.koval.trainingplannerbackend.club.membership.ClubAuthorizationService;
import com.koval.trainingplannerbackend.config.exceptions.ForbiddenOperationException;
import com.koval.trainingplannerbackend.config.exceptions.ResourceNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Optional;

/**
 * Single responsibility: chat room lifecycle (create, archive, update last-message).
 *
 * Membership management is in {@link ChatMembershipService}.
 * Read queries are in {@link ChatQueryService}.
 */
@Service
public class ChatRoomService {

    private final ChatRoomRepository roomRepository;
    private final ChatMembershipService membershipService;
    private final ClubRepository clubRepository;
    private final ClubGroupRepository clubGroupRepository;
    private final ClubAuthorizationService clubAuthorizationService;

    public ChatRoomService(ChatRoomRepository roomRepository,
                           ChatMembershipService membershipService,
                           ClubRepository clubRepository,
                           ClubGroupRepository clubGroupRepository,
                           ClubAuthorizationService clubAuthorizationService) {
        this.roomRepository = roomRepository;
        this.membershipService = membershipService;
        this.clubRepository = clubRepository;
        this.clubGroupRepository = clubGroupRepository;
        this.clubAuthorizationService = clubAuthorizationService;
    }

    /**
     * Lazy-create the CLUB room for a requesting active member and ensure their chat membership.
     * Covers legacy clubs that predate the auto-create hooks in ClubService / ClubMembershipService.
     */
    @Transactional
    public ChatRoom ensureClubRoomForMember(String userId, String clubId) {
        clubAuthorizationService.requireActiveMember(userId, clubId);
        ChatRoom room = getOrCreateClubRoom(clubId);
        membershipService.ensureMembership(room, userId, MembershipSource.AUTO, ChatMemberRole.MEMBER);
        return room;
    }

    /**
     * Lazy-create the GROUP room for a club member who belongs to the group, and ensure membership.
     * Covers legacy groups that predate the auto-create hooks in {@code ClubGroupService}.
     */
    @Transactional
    public ChatRoom ensureGroupRoomForMember(String userId, String clubId, String groupId) {
        clubAuthorizationService.requireActiveMember(userId, clubId);
        ClubGroup group = clubGroupRepository.findById(groupId)
                .orElseThrow(() -> new ResourceNotFoundException("Group not found"));
        if (!clubId.equals(group.getClubId())) {
            throw new ForbiddenOperationException("Group does not belong to this club");
        }
        if (!group.getMemberIds().contains(userId)) {
            throw new ForbiddenOperationException("Not a member of this group");
        }
        ChatRoom room = getOrCreateGroupRoom(clubId, groupId);
        membershipService.ensureMembership(room, userId, MembershipSource.AUTO, ChatMemberRole.MEMBER);
        return room;
    }

    /**
     * Lazy-create the OBJECTIVE room for any active club member. Anyone in the club may chat
     * about a shared race objective; engagement on the goal is independent of chat membership.
     */
    @Transactional
    public ChatRoom ensureObjectiveRoomForMember(String userId, String clubId, String objectiveKey, String title) {
        clubAuthorizationService.requireActiveMember(userId, clubId);
        ChatRoom room = getOrCreateObjectiveRoom(clubId, objectiveKey, title);
        membershipService.ensureMembership(room, userId, MembershipSource.AUTO, ChatMemberRole.MEMBER);
        return room;
    }

    @Transactional
    public ChatRoom getOrCreateClubRoom(String clubId) {
        return roomRepository.findByScopeAndClubIdAndScopeRefId(ChatRoomScope.CLUB, clubId, null)
                .orElseGet(() -> {
                    ChatRoom room = baseRoom(ChatRoomScope.CLUB, clubId, null);
                    room.setTitle(clubRepository.findById(clubId).map(c -> c.getName()).orElse("Club chat"));
                    return roomRepository.save(room);
                });
    }

    @Transactional
    public ChatRoom getOrCreateGroupRoom(String clubId, String groupId) {
        return roomRepository.findByScopeAndClubIdAndScopeRefId(ChatRoomScope.GROUP, clubId, groupId)
                .orElseGet(() -> {
                    ChatRoom room = baseRoom(ChatRoomScope.GROUP, clubId, groupId);
                    room.setTitle(clubGroupRepository.findById(groupId).map(ClubGroup::getName).orElse("Group chat"));
                    return roomRepository.save(room);
                });
    }

    @Transactional
    public ChatRoom getOrCreateObjectiveRoom(String clubId, String objectiveKey, String title) {
        ChatRoom room = roomRepository.findByScopeAndClubIdAndScopeRefId(ChatRoomScope.OBJECTIVE, clubId, objectiveKey)
                .orElseGet(() -> {
                    ChatRoom r = baseRoom(ChatRoomScope.OBJECTIVE, clubId, objectiveKey);
                    r.setJoinable(true);
                    r.setTitle(title != null ? title : "Club objective");
                    return roomRepository.save(r);
                });
        if (title != null && !title.equals(room.getTitle())) {
            room.setTitle(title);
            room = roomRepository.save(room);
        }
        return room;
    }

    @Transactional
    public ChatRoom getOrCreateRecurringRoom(String clubId, String templateId, String title) {
        return roomRepository.findByScopeAndClubIdAndScopeRefId(ChatRoomScope.RECURRING_SESSION, clubId, templateId)
                .orElseGet(() -> {
                    ChatRoom room = baseRoom(ChatRoomScope.RECURRING_SESSION, clubId, templateId);
                    room.setTitle(title != null ? title : "Recurring session");
                    return roomRepository.save(room);
                });
    }

    @Transactional
    public ChatRoom getOrCreateSessionRoom(String clubId, String sessionId, String title) {
        return roomRepository.findByScopeAndClubIdAndScopeRefId(ChatRoomScope.SINGLE_SESSION, clubId, sessionId)
                .orElseGet(() -> {
                    ChatRoom room = baseRoom(ChatRoomScope.SINGLE_SESSION, clubId, sessionId);
                    room.setTitle(title != null ? title : "Session");
                    return roomRepository.save(room);
                });
    }

    @Transactional
    public ChatRoom getOrCreateDirectRoom(String userA, String userB) {
        String key = directKey(userA, userB);
        return roomRepository.findByScopeAndClubIdAndScopeRefId(ChatRoomScope.DIRECT, null, key)
                .orElseGet(() -> {
                    ChatRoom room = new ChatRoom();
                    room.setScope(ChatRoomScope.DIRECT);
                    room.setClubId(null);
                    room.setScopeRefId(key);
                    room.setTitle("Direct message");
                    room.setCreatedAt(Instant.now());
                    room.setCreatedBy(userA);
                    room.setArchived(false);
                    ChatRoom saved = roomRepository.save(room);
                    membershipService.ensureMembership(saved, userA, MembershipSource.AUTO, ChatMemberRole.MEMBER);
                    membershipService.ensureMembership(saved, userB, MembershipSource.AUTO, ChatMemberRole.MEMBER);
                    return saved;
                });
    }

    @Transactional
    public void updateLastMessage(String roomId, Instant at, String preview, String senderId) {
        roomRepository.findById(roomId).ifPresent(room -> {
            room.setLastMessageAt(at);
            room.setLastMessagePreview(preview);
            room.setLastMessageSenderId(senderId);
            roomRepository.save(room);
        });
    }

    @Transactional
    public void archiveByParent(ChatRoomScope scope, String clubId, String refId) {
        Optional<ChatRoom> room = roomRepository.findByScopeAndClubIdAndScopeRefId(
                scope, clubId, scope == ChatRoomScope.CLUB ? null : refId);
        room.ifPresent(r -> { r.setArchived(true); roomRepository.save(r); });
    }

    // --- Helpers ---

    private ChatRoom baseRoom(ChatRoomScope scope, String clubId, String scopeRefId) {
        ChatRoom room = new ChatRoom();
        room.setScope(scope);
        room.setClubId(clubId);
        room.setScopeRefId(scopeRefId);
        room.setJoinable(false);
        room.setCreatedAt(Instant.now());
        room.setArchived(false);
        return room;
    }

    public static String directKey(String userA, String userB) {
        return userA.compareTo(userB) <= 0 ? userA + "|" + userB : userB + "|" + userA;
    }
}
