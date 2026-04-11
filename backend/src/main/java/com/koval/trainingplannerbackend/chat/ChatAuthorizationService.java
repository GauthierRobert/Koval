package com.koval.trainingplannerbackend.chat;

import com.koval.trainingplannerbackend.club.membership.ClubAuthorizationService;
import com.koval.trainingplannerbackend.club.membership.ClubMemberStatus;
import com.koval.trainingplannerbackend.club.membership.ClubMembership;
import com.koval.trainingplannerbackend.club.membership.ClubMembershipRepository;
import com.koval.trainingplannerbackend.config.exceptions.ForbiddenOperationException;
import com.koval.trainingplannerbackend.config.exceptions.ResourceNotFoundException;
import org.springframework.stereotype.Service;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Access control for chat rooms. Three rules:
 *
 *  1. Reading/posting in a room requires an ACTIVE ChatRoomMembership.
 *  2. Joining a room is only allowed when the room is {@code joinable}
 *     and the user is an active member of the owning club.
 *  3. Creating a DM requires the two users to share at least one active club membership.
 */
@Service
public class ChatAuthorizationService {

    private final ChatRoomRepository chatRoomRepository;
    private final ChatRoomMembershipRepository membershipRepository;
    private final ClubMembershipRepository clubMembershipRepository;
    private final ClubAuthorizationService clubAuthorizationService;

    public ChatAuthorizationService(ChatRoomRepository chatRoomRepository,
                                    ChatRoomMembershipRepository membershipRepository,
                                    ClubMembershipRepository clubMembershipRepository,
                                    ClubAuthorizationService clubAuthorizationService) {
        this.chatRoomRepository = chatRoomRepository;
        this.membershipRepository = membershipRepository;
        this.clubMembershipRepository = clubMembershipRepository;
        this.clubAuthorizationService = clubAuthorizationService;
    }

    public ChatRoom requireRoom(String roomId) {
        return chatRoomRepository.findById(roomId)
                .orElseThrow(() -> new ResourceNotFoundException("Chat room not found"));
    }

    /** Throws if the user cannot read the room. Returns the active membership on success. */
    public ChatRoomMembership requireRoomAccess(String userId, String roomId) {
        ChatRoomMembership m = membershipRepository.findByRoomIdAndUserId(roomId, userId)
                .orElseThrow(() -> new ForbiddenOperationException("Not a member of this chat room"));
        if (!m.isActive()) {
            throw new ForbiddenOperationException("Not an active member of this chat room");
        }
        return m;
    }

    /**
     * Checks whether the caller is allowed to join a joinable room.
     * Only OBJECTIVE rooms are joinable today; the caller must be an active member of the owning club.
     */
    public void requireCanJoinRoom(String userId, ChatRoom room) {
        if (!room.isJoinable()) {
            throw new ForbiddenOperationException("This room is not joinable");
        }
        if (room.getClubId() == null) {
            throw new ForbiddenOperationException("Only club rooms can be joined");
        }
        clubAuthorizationService.requireActiveMember(userId, room.getClubId());
    }

    /** Two users may DM only if they share at least one active club. */
    public void requireCanDirectMessage(String userA, String userB) {
        if (userA == null || userB == null || userA.equals(userB)) {
            throw new ForbiddenOperationException("Invalid DM target");
        }
        List<ClubMembership> aClubs = clubMembershipRepository.findByUserId(userA).stream()
                .filter(m -> m.getStatus() == ClubMemberStatus.ACTIVE)
                .collect(Collectors.toList());
        if (aClubs.isEmpty()) {
            throw new ForbiddenOperationException("You must share a club with this user to start a DM");
        }
        Set<String> aClubIds = aClubs.stream().map(ClubMembership::getClubId).collect(Collectors.toCollection(HashSet::new));
        boolean share = clubMembershipRepository.findByUserId(userB).stream()
                .filter(m -> m.getStatus() == ClubMemberStatus.ACTIVE)
                .anyMatch(m -> aClubIds.contains(m.getClubId()));
        if (!share) {
            throw new ForbiddenOperationException("You must share a club with this user to start a DM");
        }
    }
}
