package com.koval.trainingplannerbackend.chat;

import com.koval.trainingplannerbackend.club.membership.ClubAuthorizationService;
import com.koval.trainingplannerbackend.club.membership.ClubMemberStatus;
import com.koval.trainingplannerbackend.club.membership.ClubMembership;
import com.koval.trainingplannerbackend.club.membership.ClubMembershipRepository;
import com.koval.trainingplannerbackend.config.exceptions.ForbiddenOperationException;
import com.koval.trainingplannerbackend.config.exceptions.ResourceNotFoundException;
import org.springframework.stereotype.Service;

import java.util.Set;
import java.util.stream.Collectors;

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

    public ChatRoomMembership requireRoomAccess(String userId, String roomId) {
        ChatRoomMembership m = membershipRepository.findByRoomIdAndUserId(roomId, userId)
                .orElseThrow(() -> new ForbiddenOperationException("Not a member of this chat room"));
        if (!m.isActive()) throw new ForbiddenOperationException("Not an active member of this chat room");
        return m;
    }

    public void requireCanJoinRoom(String userId, ChatRoom room) {
        if (!room.isJoinable()) throw new ForbiddenOperationException("This room is not joinable");
        if (room.getClubId() == null) throw new ForbiddenOperationException("Only club rooms can be joined");
        clubAuthorizationService.requireActiveMember(userId, room.getClubId());
    }

    public void requireCanDirectMessage(String userA, String userB) {
        if (userA == null || userB == null || userA.equals(userB)) {
            throw new ForbiddenOperationException("Invalid DM target");
        }
        Set<String> aClubs = getActiveClubIds(userA);
        if (aClubs.isEmpty() || getActiveClubIds(userB).stream().noneMatch(aClubs::contains)) {
            throw new ForbiddenOperationException("You must share a club with this user to start a DM");
        }
    }

    private Set<String> getActiveClubIds(String userId) {
        return clubMembershipRepository.findByUserId(userId).stream()
                .filter(m -> m.getStatus() == ClubMemberStatus.ACTIVE)
                .map(ClubMembership::getClubId)
                .collect(Collectors.toSet());
    }
}
