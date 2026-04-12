package com.koval.trainingplannerbackend.chat;

import com.koval.trainingplannerbackend.auth.User;
import com.koval.trainingplannerbackend.auth.UserService;
import com.koval.trainingplannerbackend.chat.dto.ChatRoomResponse;
import com.koval.trainingplannerbackend.chat.dto.ChatRoomSummaryResponse;
import com.koval.trainingplannerbackend.config.exceptions.ResourceNotFoundException;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Single responsibility: read-only queries for chat rooms.
 * Room listing, detail, display-title resolution, and unread counts.
 */
@Service
public class ChatQueryService {

    private final ChatRoomRepository roomRepository;
    private final ChatRoomMembershipRepository membershipRepository;
    private final ChatMessageCustomRepository messageCustomRepository;
    private final UserService userService;

    public ChatQueryService(ChatRoomRepository roomRepository,
                            ChatRoomMembershipRepository membershipRepository,
                            ChatMessageCustomRepository messageCustomRepository,
                            UserService userService) {
        this.roomRepository = roomRepository;
        this.membershipRepository = membershipRepository;
        this.messageCustomRepository = messageCustomRepository;
        this.userService = userService;
    }

    public List<ChatRoomSummaryResponse> listMyRooms(String userId) {
        List<ChatRoomMembership> memberships = membershipRepository.findByUserIdAndActiveTrue(userId);
        if (memberships.isEmpty()) return List.of();

        Map<String, ChatRoomMembership> membershipByRoom = memberships.stream()
                .collect(Collectors.toMap(ChatRoomMembership::getRoomId, m -> m, (a, b) -> a));

        List<ChatRoom> rooms = new ArrayList<>();
        roomRepository.findAllById(membershipByRoom.keySet()).forEach(rooms::add);

        Map<String, Instant> needsCount = new HashMap<>();
        for (ChatRoom room : rooms) {
            if (room.isArchived()) continue;
            ChatRoomMembership m = membershipByRoom.get(room.getId());
            Instant lastRead = m.getLastReadAt() != null ? m.getLastReadAt() : Instant.EPOCH;
            if (room.getLastMessageAt() != null && room.getLastMessageAt().isAfter(lastRead)) {
                needsCount.put(room.getId(), lastRead);
            }
        }
        Map<String, Long> unreadCounts = messageCustomRepository.batchUnreadCounts(needsCount);

        List<ChatRoomSummaryResponse> out = new ArrayList<>();
        for (ChatRoom room : rooms) {
            if (room.isArchived()) continue;
            ChatRoomMembership m = membershipByRoom.get(room.getId());
            out.add(new ChatRoomSummaryResponse(
                    room.getId(), room.getScope(), room.getClubId(), room.getScopeRefId(),
                    displayTitleFor(room, userId), room.isJoinable(), m.isMuted(),
                    room.getLastMessageAt(), room.getLastMessagePreview(), room.getLastMessageSenderId(),
                    unreadCounts.getOrDefault(room.getId(), 0L),
                    otherUserIdForDirect(room, userId)
            ));
        }

        out.sort(Comparator.comparing(
                (ChatRoomSummaryResponse r) -> r.lastMessageAt() != null ? r.lastMessageAt() : Instant.EPOCH,
                Comparator.reverseOrder()
        ));
        return out;
    }

    public ChatRoomResponse getRoomDetail(String userId, String roomId) {
        ChatRoom room = roomRepository.findById(roomId)
                .orElseThrow(() -> new ResourceNotFoundException("Chat room not found"));
        Optional<ChatRoomMembership> maybe = membershipRepository.findByRoomIdAndUserId(roomId, userId);
        return new ChatRoomResponse(
                room.getId(), room.getScope(), room.getClubId(), room.getScopeRefId(),
                displayTitleFor(room, userId), room.isJoinable(), room.isArchived(),
                room.getCreatedAt(), room.getCreatedBy(), room.getLastMessageAt(),
                maybe.map(ChatRoomMembership::isActive).orElse(false),
                maybe.map(ChatRoomMembership::isMuted).orElse(false),
                maybe.map(ChatRoomMembership::getLastReadAt).orElse(null),
                otherUserIdForDirect(room, userId)
        );
    }

    public Optional<ChatRoom> findByParent(ChatRoomScope scope, String clubId, String refId) {
        return roomRepository.findByScopeAndClubIdAndScopeRefId(
                scope, clubId, scope == ChatRoomScope.CLUB ? null : refId);
    }

    // --- Display helpers ---

    private String otherUserIdForDirect(ChatRoom room, String viewer) {
        if (room.getScope() != ChatRoomScope.DIRECT || room.getScopeRefId() == null) return null;
        String[] parts = room.getScopeRefId().split("\\|");
        return parts.length == 2 ? (parts[0].equals(viewer) ? parts[1] : parts[0]) : null;
    }

    private String displayTitleFor(ChatRoom room, String viewer) {
        if (room.getScope() == ChatRoomScope.DIRECT) {
            String other = otherUserIdForDirect(room, viewer);
            if (other != null) {
                User u = userService.findById(other).orElse(null);
                if (u != null && u.getDisplayName() != null) return u.getDisplayName();
                return other;
            }
        }
        return room.getTitle();
    }
}
