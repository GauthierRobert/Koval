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
    private final ChatMessageService chatMessageService;
    private final ChatAuthorizationService chatAuthorizationService;

    public ChatController(ChatRoomService chatRoomService,
                          ChatMessageService chatMessageService,
                          ChatAuthorizationService chatAuthorizationService) {
        this.chatRoomService = chatRoomService;
        this.chatMessageService = chatMessageService;
        this.chatAuthorizationService = chatAuthorizationService;
    }

    @GetMapping("/rooms")
    public ResponseEntity<List<ChatRoomSummaryResponse>> listMyRooms() {
        String userId = SecurityUtils.getCurrentUserId();
        return ResponseEntity.ok(chatRoomService.listMyRooms(userId));
    }

    @GetMapping("/rooms/{roomId}")
    public ResponseEntity<ChatRoomResponse> getRoom(@PathVariable String roomId) {
        String userId = SecurityUtils.getCurrentUserId();
        return ResponseEntity.ok(chatRoomService.getRoomDetail(userId, roomId));
    }

    @GetMapping("/rooms/by-parent")
    public ResponseEntity<ChatRoomResponse> findByParent(
            @RequestParam ChatRoomScope scope,
            @RequestParam String clubId,
            @RequestParam(required = false) String refId) {
        String userId = SecurityUtils.getCurrentUserId();
        return chatRoomService.findByParent(scope, clubId, refId)
                .map(room -> ResponseEntity.ok(chatRoomService.getRoomDetail(userId, room.getId())))
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/rooms/{roomId}/messages")
    public ResponseEntity<List<ChatMessageResponse>> getMessages(
            @PathVariable String roomId,
            @RequestParam(required = false) String before,
            @RequestParam(required = false) Integer size) {
        String userId = SecurityUtils.getCurrentUserId();
        Instant parsed = null;
        if (before != null && !before.isBlank()) {
            try {
                parsed = Instant.parse(before);
            } catch (DateTimeParseException e) {
                return ResponseEntity.badRequest().build();
            }
        }
        return ResponseEntity.ok(chatMessageService.getPage(userId, roomId, parsed, size));
    }

    @PostMapping("/rooms/{roomId}/messages")
    public ResponseEntity<ChatMessageResponse> postMessage(
            @PathVariable String roomId,
            @RequestBody PostMessageRequest req) {
        String userId = SecurityUtils.getCurrentUserId();
        return ResponseEntity.ok(chatMessageService.post(userId, roomId, req.content(), req.clientNonce()));
    }

    @PostMapping("/rooms/{roomId}/join")
    public ResponseEntity<Void> join(@PathVariable String roomId) {
        String userId = SecurityUtils.getCurrentUserId();
        // Permission check happens inside joinRoom via the joinable flag;
        // additional club-level check delegated to ChatAuthorizationService.
        ChatRoom room = chatAuthorizationService.requireRoom(roomId);
        chatAuthorizationService.requireCanJoinRoom(userId, room);
        chatRoomService.joinRoom(userId, roomId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/rooms/{roomId}/leave")
    public ResponseEntity<Void> leave(@PathVariable String roomId) {
        String userId = SecurityUtils.getCurrentUserId();
        chatRoomService.leaveRoom(userId, roomId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/rooms/{roomId}/mute")
    public ResponseEntity<Void> mute(
            @PathVariable String roomId,
            @RequestBody MembershipUpdateRequest req) {
        String userId = SecurityUtils.getCurrentUserId();
        chatRoomService.setMuted(userId, roomId, Boolean.TRUE.equals(req.muted()));
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/rooms/{roomId}/read")
    public ResponseEntity<Void> markRead(@PathVariable String roomId) {
        String userId = SecurityUtils.getCurrentUserId();
        chatRoomService.markRead(userId, roomId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/rooms/direct")
    public ResponseEntity<ChatRoomResponse> createOrGetDirect(@RequestBody CreateDirectRoomRequest req) {
        String userId = SecurityUtils.getCurrentUserId();
        chatAuthorizationService.requireCanDirectMessage(userId, req.otherUserId());
        ChatRoom room = chatRoomService.getOrCreateDirectRoom(userId, req.otherUserId());
        return ResponseEntity.ok(chatRoomService.getRoomDetail(userId, room.getId()));
    }

    @DeleteMapping("/messages/{messageId}")
    public ResponseEntity<Void> deleteMessage(@PathVariable String messageId) {
        String userId = SecurityUtils.getCurrentUserId();
        chatMessageService.softDelete(userId, messageId);
        return ResponseEntity.noContent().build();
    }
}
