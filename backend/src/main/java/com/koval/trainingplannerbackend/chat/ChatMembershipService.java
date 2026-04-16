package com.koval.trainingplannerbackend.chat;

import com.koval.trainingplannerbackend.config.exceptions.ForbiddenOperationException;
import com.koval.trainingplannerbackend.config.exceptions.ResourceNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Single responsibility: chat room membership lifecycle.
 * Handles joins, leaves, mutes, mark-read, and AUTO-member reconciliation.
 */
@Service
public class ChatMembershipService {

    private final ChatRoomRepository roomRepository;
    private final ChatRoomMembershipRepository membershipRepository;

    public ChatMembershipService(ChatRoomRepository roomRepository,
                                 ChatRoomMembershipRepository membershipRepository) {
        this.roomRepository = roomRepository;
        this.membershipRepository = membershipRepository;
    }

    /** Add or reactivate a single member on a room. Idempotent. */
    @Transactional
    public ChatRoomMembership ensureMembership(ChatRoom room, String userId, MembershipSource source, ChatMemberRole role) {
        Optional<ChatRoomMembership> existing = membershipRepository.findByRoomIdAndUserId(room.getId(), userId);
        if (existing.isPresent()) {
            ChatRoomMembership m = existing.get();
            if (!m.getActive()) {
                m.setActive(true);
                m.setJoinedAt(Instant.now());
            }
            if (m.getSource() == null || m.getSource() == MembershipSource.AUTO) {
                if (source == MembershipSource.SELF_JOINED) m.setSource(MembershipSource.SELF_JOINED);
            }
            return membershipRepository.save(m);
        }
        ChatRoomMembership m = new ChatRoomMembership();
        m.setRoomId(room.getId());
        m.setUserId(userId);
        m.setClubId(room.getClubId());
        m.setJoinedAt(Instant.now());
        m.setLastReadAt(Instant.EPOCH);
        m.setMuted(false);
        m.setRole(role);
        m.setSource(source);
        m.setActive(true);
        return membershipRepository.save(m);
    }

    /**
     * Reconcile AUTO-sourced memberships for a room against a target user id set.
     * SELF_JOINED memberships are never touched.
     */
    @Transactional
    public void syncAutoMembers(String roomId, Set<String> expectedUserIds) {
        ChatRoom room = roomRepository.findById(roomId)
                .orElseThrow(() -> new ResourceNotFoundException("Chat room not found"));
        List<ChatRoomMembership> all = membershipRepository.findByRoomId(roomId);
        Map<String, ChatRoomMembership> byUser = all.stream()
                .collect(Collectors.toMap(ChatRoomMembership::getUserId, m -> m, (a, b) -> a));

        for (String userId : expectedUserIds) {
            ChatRoomMembership existing = byUser.get(userId);
            if (existing == null) {
                ensureMembership(room, userId, MembershipSource.AUTO, ChatMemberRole.MEMBER);
            } else if (!existing.getActive()) {
                existing.setActive(true);
                existing.setJoinedAt(Instant.now());
                if (existing.getSource() == null) existing.setSource(MembershipSource.AUTO);
                membershipRepository.save(existing);
            }
        }

        Set<String> expectedSet = new HashSet<>(expectedUserIds);
        for (ChatRoomMembership m : all) {
            if (m.getSource() == MembershipSource.AUTO && m.getActive() && !expectedSet.contains(m.getUserId())) {
                m.setActive(false);
                membershipRepository.save(m);
            }
        }
    }

    /** Deactivate every chat membership for a user within a club. */
    @Transactional
    public void deactivateAllForUserInClub(String clubId, String userId) {
        for (ChatRoomMembership m : membershipRepository.findByClubIdAndUserId(clubId, userId)) {
            if (m.getActive()) {
                m.setActive(false);
                membershipRepository.save(m);
            }
        }
    }

    @Transactional
    public ChatRoomMembership joinRoom(String userId, String roomId) {
        ChatRoom room = roomRepository.findById(roomId)
                .orElseThrow(() -> new ResourceNotFoundException("Chat room not found"));
        if (!room.isJoinable()) throw new ForbiddenOperationException("This room is not joinable");
        return ensureMembership(room, userId, MembershipSource.SELF_JOINED, ChatMemberRole.MEMBER);
    }

    @Transactional
    public void leaveRoom(String userId, String roomId) {
        ChatRoomMembership m = membershipRepository.findByRoomIdAndUserId(roomId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Not a member of this room"));
        m.setActive(false);
        membershipRepository.save(m);
    }

    @Transactional
    public void setMuted(String userId, String roomId, boolean muted) {
        ChatRoomMembership m = membershipRepository.findByRoomIdAndUserId(roomId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Not a member of this room"));
        m.setMuted(muted);
        membershipRepository.save(m);
    }

    @Transactional
    public void markRead(String userId, String roomId) {
        ChatRoomMembership m = membershipRepository.findByRoomIdAndUserId(roomId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Not a member of this room"));
        m.setLastReadAt(Instant.now());
        membershipRepository.save(m);
    }
}
