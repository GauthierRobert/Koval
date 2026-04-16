package com.koval.trainingplannerbackend.chat;

import com.koval.trainingplannerbackend.auth.SecurityUtils;
import com.koval.trainingplannerbackend.chat.dto.ChatMessageResponse;
import com.koval.trainingplannerbackend.chat.dto.ChatRoomResponse;
import com.koval.trainingplannerbackend.chat.dto.ChatRoomSummaryResponse;
import com.koval.trainingplannerbackend.chat.dto.CreateDirectRoomRequest;
import com.koval.trainingplannerbackend.chat.dto.MembershipUpdateRequest;
import com.koval.trainingplannerbackend.chat.dto.PostMessageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.List;

@RestController
@RequestMapping("/api/chat")
public class ChatController {

    private final ChatRoomService chatRoomService;
    private final ChatMembershipService chatMembershipService;
    private final ChatQueryService chatQueryService;
    private final ChatMessageService chatMessageService;
    private final ChatAuthorizationService chatAuthorizationService;

    public ChatController(ChatRoomService chatRoomService,
                          ChatMembershipService chatMembershipService,
                          ChatQueryService chatQueryService,
                          ChatMessageService chatMessageService,
                          ChatAuthorizationService chatAuthorizationService) {
        this.chatRoomService = chatRoomService;
        this.chatMembershipService = chatMembershipService;
        this.chatQueryService = chatQueryService;
        this.chatMessageService = chatMessageService;
        this.chatAuthorizationService = chatAuthorizationService;
    }

    @GetMapping("/rooms")
    public ResponseEntity<List<ChatRoomSummaryResponse>> listMyRooms() {
        return ResponseEntity.ok(chatQueryService.listMyRooms(SecurityUtils.getCurrentUserId()));
    }

    @GetMapping("/rooms/{roomId}")
    public ResponseEntity<ChatRoomResponse> getRoom(@PathVariable String roomId) {
        return ResponseEntity.ok(chatQueryService.getRoomDetail(SecurityUtils.getCurrentUserId(), roomId));
    }

    @GetMapping("/rooms/by-parent")
    public ResponseEntity<ChatRoomResponse> findByParent(
            @RequestParam ChatRoomScope scope,
            @RequestParam String clubId,
            @RequestParam(required = false) String refId,
            @RequestParam(required = false) String title) {
        String userId = SecurityUtils.getCurrentUserId();
        switch (scope) {
            case CLUB -> {
                ChatRoom room = chatRoomService.ensureClubRoomForMember(userId, clubId);
                return ResponseEntity.ok(chatQueryService.getRoomDetail(userId, room.getId()));
            }
            case GROUP -> {
                if (refId == null) return ResponseEntity.badRequest().build();
                ChatRoom room = chatRoomService.ensureGroupRoomForMember(userId, clubId, refId);
                return ResponseEntity.ok(chatQueryService.getRoomDetail(userId, room.getId()));
            }
            case OBJECTIVE -> {
                if (refId == null) return ResponseEntity.badRequest().build();
                ChatRoom room = chatRoomService.ensureObjectiveRoomForMember(userId, clubId, refId, title);
                return ResponseEntity.ok(chatQueryService.getRoomDetail(userId, room.getId()));
            }
            default -> {
                return chatQueryService.findByParent(scope, clubId, refId)
                        .map(room -> ResponseEntity.ok(chatQueryService.getRoomDetail(userId, room.getId())))
                        .orElse(ResponseEntity.notFound().build());
            }
        }
    }

    @GetMapping("/rooms/{roomId}/messages")
    public ResponseEntity<List<ChatMessageResponse>> getMessages(
            @PathVariable String roomId,
            @RequestParam(required = false) String before,
            @RequestParam(required = false) Integer size) {
        Instant parsed = null;
        if (before != null && !before.isBlank()) {
            try { parsed = Instant.parse(before); }
            catch (DateTimeParseException e) { return ResponseEntity.badRequest().build(); }
        }
        return ResponseEntity.ok(chatMessageService.getPage(SecurityUtils.getCurrentUserId(), roomId, parsed, size));
    }

    @PostMapping("/rooms/{roomId}/messages")
    public ResponseEntity<ChatMessageResponse> postMessage(
            @PathVariable String roomId, @RequestBody PostMessageRequest req) {
        return ResponseEntity.ok(chatMessageService.post(SecurityUtils.getCurrentUserId(), roomId, req.content(), req.clientNonce()));
    }

    @PostMapping("/rooms/{roomId}/join")
    public ResponseEntity<Void> join(@PathVariable String roomId) {
        String userId = SecurityUtils.getCurrentUserId();
        ChatRoom room = chatAuthorizationService.requireRoom(roomId);
        chatAuthorizationService.requireCanJoinRoom(userId, room);
        chatMembershipService.joinRoom(userId, roomId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/rooms/{roomId}/leave")
    public ResponseEntity<Void> leave(@PathVariable String roomId) {
        chatMembershipService.leaveRoom(SecurityUtils.getCurrentUserId(), roomId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/rooms/{roomId}/mute")
    public ResponseEntity<Void> mute(@PathVariable String roomId, @RequestBody MembershipUpdateRequest req) {
        chatMembershipService.setMuted(SecurityUtils.getCurrentUserId(), roomId, Boolean.TRUE.equals(req.muted()));
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/rooms/{roomId}/read")
    public ResponseEntity<Void> markRead(@PathVariable String roomId) {
        chatMembershipService.markRead(SecurityUtils.getCurrentUserId(), roomId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/rooms/direct")
    public ResponseEntity<ChatRoomResponse> createOrGetDirect(@RequestBody CreateDirectRoomRequest req) {
        String userId = SecurityUtils.getCurrentUserId();
        chatAuthorizationService.requireCanDirectMessage(userId, req.otherUserId());
        ChatRoom room = chatRoomService.getOrCreateDirectRoom(userId, req.otherUserId());
        return ResponseEntity.ok(chatQueryService.getRoomDetail(userId, room.getId()));
    }

    @DeleteMapping("/messages/{messageId}")
    public ResponseEntity<Void> deleteMessage(@PathVariable String messageId) {
        chatMessageService.softDelete(SecurityUtils.getCurrentUserId(), messageId);
        return ResponseEntity.noContent().build();
    }
}
