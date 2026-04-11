package com.koval.trainingplannerbackend.chat;

import com.koval.trainingplannerbackend.auth.User;
import com.koval.trainingplannerbackend.auth.UserService;
import com.koval.trainingplannerbackend.chat.dto.ChatRoomResponse;
import com.koval.trainingplannerbackend.chat.dto.ChatRoomSummaryResponse;
import com.koval.trainingplannerbackend.club.ClubRepository;
import com.koval.trainingplannerbackend.club.group.ClubGroup;
import com.koval.trainingplannerbackend.club.group.ClubGroupRepository;
import com.koval.trainingplannerbackend.config.exceptions.ForbiddenOperationException;
import com.koval.trainingplannerbackend.config.exceptions.ResourceNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Orchestrates chat room lifecycle and membership.
 *
 * Rooms are created lazily on first access via {@code getOrCreate...} helpers so
 * upstream integration hooks (ClubService, ClubGroupService, etc.) can call them
 * without worrying about duplicates — the compound unique indices are the source of truth.
 *
 * Membership sync uses {@link MembershipSource} to distinguish AUTO memberships
 * (reconciled from the parent entity roster) from SELF_JOINED ones (only OBJECTIVE rooms),
 * so reconciling the AUTO set never stomps on a user who manually joined a joinable room.
 */
@Service
public class ChatRoomService {

    private final ChatRoomRepository roomRepository;
    private final ChatRoomMembershipRepository membershipRepository;
    private final ChatMessageRepository messageRepository;
    private final ClubRepository clubRepository;
    private final ClubGroupRepository clubGroupRepository;
    private final UserService userService;

    public ChatRoomService(ChatRoomRepository roomRepository,
                           ChatRoomMembershipRepository membershipRepository,
                           ChatMessageRepository messageRepository,
                           ClubRepository clubRepository,
                           ClubGroupRepository clubGroupRepository,
                           UserService userService) {
        this.roomRepository = roomRepository;
        this.membershipRepository = membershipRepository;
        this.messageRepository = messageRepository;
        this.clubRepository = clubRepository;
        this.clubGroupRepository = clubGroupRepository;
        this.userService = userService;
    }

    // ---------- Room creation helpers ----------

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
        // Keep the denormalized title fresh if caller supplied one.
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
                    // The sorted-pair key goes into scopeRefId so the single compound unique
                    // index covers DM dedupe without needing a separate field/partial filter.
                    room.setScopeRefId(key);
                    room.setTitle("Direct message");
                    room.setCreatedAt(Instant.now());
                    room.setCreatedBy(userA);
                    room.setArchived(false);
                    ChatRoom saved = roomRepository.save(room);

                    // Pre-seed both memberships so the DM shows up in both users' room lists.
                    ensureMembership(saved, userA, MembershipSource.AUTO, ChatMemberRole.MEMBER);
                    ensureMembership(saved, userB, MembershipSource.AUTO, ChatMemberRole.MEMBER);
                    return saved;
                });
    }

    // ---------- Membership management ----------

    /**
     * Reconcile AUTO-sourced memberships for a room against a target user id set.
     * Users in {@code expectedUserIds} but not yet active get added (or reactivated).
     * Users currently active with source=AUTO but not in the expected set get deactivated.
     * SELF_JOINED memberships are never touched by this sync.
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
            } else if (!existing.isActive()) {
                existing.setActive(true);
                existing.setJoinedAt(Instant.now());
                if (existing.getSource() == null) existing.setSource(MembershipSource.AUTO);
                membershipRepository.save(existing);
            }
        }

        Set<String> expectedSet = new HashSet<>(expectedUserIds);
        for (ChatRoomMembership m : all) {
            if (m.getSource() == MembershipSource.AUTO && m.isActive() && !expectedSet.contains(m.getUserId())) {
                m.setActive(false);
                membershipRepository.save(m);
            }
        }
    }

    /** Add or reactivate a single member on a room. Idempotent. */
    @Transactional
    public ChatRoomMembership ensureMembership(ChatRoom room, String userId, MembershipSource source, ChatMemberRole role) {
        Optional<ChatRoomMembership> existing = membershipRepository.findByRoomIdAndUserId(room.getId(), userId);
        if (existing.isPresent()) {
            ChatRoomMembership m = existing.get();
            if (!m.isActive()) {
                m.setActive(true);
                m.setJoinedAt(Instant.now());
            }
            if (m.getSource() == null || m.getSource() == MembershipSource.AUTO) {
                // Allow upgrading AUTO to SELF_JOINED but not downgrading back.
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
     * Deactivate every chat membership for {@code userId} within the given club.
     * Invoked when a user leaves/is kicked from a club, so they disappear from all
     * club-scoped chat rooms at once while preserving their lastReadAt for potential rejoin.
     */
    @Transactional
    public void deactivateAllForUserInClub(String clubId, String userId) {
        List<ChatRoomMembership> memberships = membershipRepository.findByClubIdAndUserId(clubId, userId);
        for (ChatRoomMembership m : memberships) {
            if (m.isActive()) {
                m.setActive(false);
                membershipRepository.save(m);
            }
        }
    }

    @Transactional
    public ChatRoomMembership joinRoom(String userId, String roomId) {
        ChatRoom room = roomRepository.findById(roomId)
                .orElseThrow(() -> new ResourceNotFoundException("Chat room not found"));
        if (!room.isJoinable()) {
            throw new ForbiddenOperationException("This room is not joinable");
        }
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
    public ChatRoomMembership setMuted(String userId, String roomId, boolean muted) {
        ChatRoomMembership m = membershipRepository.findByRoomIdAndUserId(roomId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Not a member of this room"));
        m.setMuted(muted);
        return membershipRepository.save(m);
    }

    @Transactional
    public void markRead(String userId, String roomId) {
        ChatRoomMembership m = membershipRepository.findByRoomIdAndUserId(roomId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Not a member of this room"));
        m.setLastReadAt(Instant.now());
        membershipRepository.save(m);
    }

    // ---------- Room listing ----------

    public List<ChatRoomSummaryResponse> listMyRooms(String userId) {
        List<ChatRoomMembership> memberships = membershipRepository.findByUserIdAndActiveTrue(userId);
        if (memberships.isEmpty()) return List.of();

        Map<String, ChatRoomMembership> membershipByRoom = memberships.stream()
                .collect(Collectors.toMap(ChatRoomMembership::getRoomId, m -> m, (a, b) -> a));

        List<ChatRoom> rooms = new ArrayList<>();
        roomRepository.findAllById(membershipByRoom.keySet()).forEach(rooms::add);

        List<ChatRoomSummaryResponse> out = new ArrayList<>();
        for (ChatRoom room : rooms) {
            if (room.isArchived()) continue;
            ChatRoomMembership m = membershipByRoom.get(room.getId());
            long unread = messageRepository.countByRoomIdAndCreatedAtGreaterThan(
                    room.getId(),
                    m.getLastReadAt() != null ? m.getLastReadAt() : Instant.EPOCH
            );
            String otherUserId = otherUserIdForDirect(room, userId);
            String title = displayTitleFor(room, userId);
            out.add(new ChatRoomSummaryResponse(
                    room.getId(),
                    room.getScope(),
                    room.getClubId(),
                    room.getScopeRefId(),
                    title,
                    room.isJoinable(),
                    m.isMuted(),
                    room.getLastMessageAt(),
                    room.getLastMessagePreview(),
                    room.getLastMessageSenderId(),
                    unread,
                    otherUserId
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
        boolean isMember = maybe.map(ChatRoomMembership::isActive).orElse(false);
        Instant lastReadAt = maybe.map(ChatRoomMembership::getLastReadAt).orElse(null);
        boolean muted = maybe.map(ChatRoomMembership::isMuted).orElse(false);
        return new ChatRoomResponse(
                room.getId(),
                room.getScope(),
                room.getClubId(),
                room.getScopeRefId(),
                displayTitleFor(room, userId),
                room.isJoinable(),
                room.isArchived(),
                room.getCreatedAt(),
                room.getCreatedBy(),
                room.getLastMessageAt(),
                isMember,
                muted,
                lastReadAt,
                otherUserIdForDirect(room, userId)
        );
    }

    public Optional<ChatRoom> findByParent(ChatRoomScope scope, String clubId, String refId) {
        if (scope == ChatRoomScope.CLUB) {
            return roomRepository.findByScopeAndClubIdAndScopeRefId(scope, clubId, null);
        }
        return roomRepository.findByScopeAndClubIdAndScopeRefId(scope, clubId, refId);
    }

    // ---------- Internals ----------

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

    /** Build the stable sorted key used to dedupe DM rooms. */
    public static String directKey(String userA, String userB) {
        return userA.compareTo(userB) <= 0 ? userA + "|" + userB : userB + "|" + userA;
    }

    private String otherUserIdForDirect(ChatRoom room, String viewer) {
        if (room.getScope() != ChatRoomScope.DIRECT || room.getScopeRefId() == null) return null;
        String[] parts = room.getScopeRefId().split("\\|");
        if (parts.length != 2) return null;
        return parts[0].equals(viewer) ? parts[1] : parts[0];
    }

    /** For DMs show the peer's display name; for everything else use the room's denormalized title. */
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

    /** Touch last-message fields after a new post. Called by ChatMessageService. */
    @Transactional
    public void updateLastMessage(String roomId, Instant at, String preview, String senderId) {
        roomRepository.findById(roomId).ifPresent(room -> {
            room.setLastMessageAt(at);
            room.setLastMessagePreview(preview);
            room.setLastMessageSenderId(senderId);
            roomRepository.save(room);
        });
    }

    /** Archive a room when its parent entity is deleted (e.g., group delete). */
    @Transactional
    public void archiveByParent(ChatRoomScope scope, String clubId, String refId) {
        findByParent(scope, clubId, refId).ifPresent(room -> {
            room.setArchived(true);
            roomRepository.save(room);
        });
    }
}
