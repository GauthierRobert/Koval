package com.koval.trainingplannerbackend.chat;

import com.koval.trainingplannerbackend.auth.User;
import com.koval.trainingplannerbackend.auth.UserService;
import com.koval.trainingplannerbackend.chat.dto.ChatMessageResponse;
import com.koval.trainingplannerbackend.config.exceptions.ForbiddenOperationException;
import com.koval.trainingplannerbackend.config.exceptions.ResourceNotFoundException;
import com.koval.trainingplannerbackend.config.exceptions.ValidationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
public class ChatMessageService {

    private static final int MAX_MESSAGE_LENGTH = 4000;
    private static final int PREVIEW_LENGTH = 120;
    private static final int DEFAULT_PAGE_SIZE = 50;
    private static final int MAX_PAGE_SIZE = 100;

    private final ChatMessageRepository messageRepository;
    private final ChatRoomMembershipRepository membershipRepository;
    private final ChatAuthorizationService authorizationService;
    private final ChatRoomService chatRoomService;
    private final ChatSseBroadcaster broadcaster;
    private final UserService userService;

    public ChatMessageService(ChatMessageRepository messageRepository,
                              ChatRoomMembershipRepository membershipRepository,
                              ChatAuthorizationService authorizationService,
                              ChatRoomService chatRoomService,
                              ChatSseBroadcaster broadcaster,
                              UserService userService) {
        this.messageRepository = messageRepository;
        this.membershipRepository = membershipRepository;
        this.authorizationService = authorizationService;
        this.chatRoomService = chatRoomService;
        this.broadcaster = broadcaster;
        this.userService = userService;
    }

    @Transactional
    public ChatMessageResponse post(String userId, String roomId, String content, String clientNonce) {
        if (content == null || content.isBlank()) {
            throw new ValidationException("Message content cannot be empty");
        }
        if (content.length() > MAX_MESSAGE_LENGTH) {
            throw new ValidationException("Message too long (max " + MAX_MESSAGE_LENGTH + ")");
        }

        authorizationService.requireRoomAccess(userId, roomId);

        // Idempotent retry: if a message with the same nonce already exists in this room, return it.
        if (clientNonce != null && !clientNonce.isBlank()) {
            Optional<ChatMessage> dup = messageRepository.findFirstByRoomIdAndClientNonce(roomId, clientNonce);
            if (dup.isPresent()) {
                return toResponse(dup.get(), lookupDisplayNames(List.of(dup.get().getSenderId())));
            }
        }

        ChatMessage msg = new ChatMessage();
        msg.setRoomId(roomId);
        msg.setSenderId(userId);
        msg.setContent(content);
        msg.setCreatedAt(Instant.now());
        msg.setDeleted(false);
        msg.setClientNonce(clientNonce);
        msg.setType(ChatMessageType.TEXT);
        msg = messageRepository.save(msg);

        String preview = content.length() > PREVIEW_LENGTH ? content.substring(0, PREVIEW_LENGTH) : content;
        chatRoomService.updateLastMessage(roomId, msg.getCreatedAt(), preview, userId);

        ChatMessageResponse response = toResponse(msg, lookupDisplayNames(List.of(userId)));

        // Fan out via SSE to all active members (including the sender — simplifies client sync).
        List<ChatRoomMembership> members = membershipRepository.findByRoomIdAndActiveTrue(roomId);
        for (ChatRoomMembership m : members) {
            broadcaster.broadcast(m.getUserId(), "chat_message", response);
        }

        return response;
    }

    public List<ChatMessageResponse> getPage(String userId, String roomId, Instant before, Integer sizeParam) {
        authorizationService.requireRoomAccess(userId, roomId);

        int size = sizeParam == null || sizeParam <= 0 ? DEFAULT_PAGE_SIZE : Math.min(sizeParam, MAX_PAGE_SIZE);
        List<ChatMessage> page;
        if (before == null) {
            page = messageRepository.findByRoomIdOrderByCreatedAtDesc(roomId, PageRequest.of(0, size));
        } else {
            page = messageRepository.findByRoomIdAndCreatedAtLessThanOrderByCreatedAtDesc(
                    roomId, before, PageRequest.of(0, size));
        }

        // Batch user lookup for sender display names.
        List<String> senderIds = page.stream().map(ChatMessage::getSenderId).distinct().toList();
        Map<String, User> users = lookupDisplayNames(senderIds);

        // Return oldest-first so the frontend can append on infinite-scroll without reversing.
        List<ChatMessageResponse> out = page.stream()
                .map(m -> toResponse(m, users))
                .collect(Collectors.toCollection(ArrayList::new));
        Collections.reverse(out);
        return out;
    }

    @Transactional
    public void softDelete(String userId, String messageId) {
        ChatMessage msg = messageRepository.findById(messageId)
                .orElseThrow(() -> new ResourceNotFoundException("Message not found"));
        if (!msg.getSenderId().equals(userId)) {
            throw new ForbiddenOperationException("Only the sender can delete a message");
        }
        msg.setDeleted(true);
        msg.setContent("");
        messageRepository.save(msg);
    }

    private Map<String, User> lookupDisplayNames(List<String> userIds) {
        if (userIds.isEmpty()) return Map.of();
        return userService.findAllById(userIds).stream()
                .collect(Collectors.toMap(User::getId, u -> u));
    }

    private ChatMessageResponse toResponse(ChatMessage m, Map<String, User> users) {
        User sender = users.get(m.getSenderId());
        return new ChatMessageResponse(
                m.getId(),
                m.getRoomId(),
                m.getSenderId(),
                sender != null ? sender.getDisplayName() : m.getSenderId(),
                sender != null ? sender.getProfilePicture() : null,
                m.getDeleted() ? "" : m.getContent(),
                m.getCreatedAt(),
                m.getEditedAt(),
                m.getDeleted(),
                m.getType()
        );
    }
}
