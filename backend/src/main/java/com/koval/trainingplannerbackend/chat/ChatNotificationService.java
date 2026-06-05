package com.koval.trainingplannerbackend.chat;

import com.koval.trainingplannerbackend.chat.dto.ChatMessageResponse;
import com.koval.trainingplannerbackend.notification.NotificationService;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Builds and dispatches push notifications for new chat messages.
 * SSE only reaches users with the app open — push covers everyone else.
 */
@Service
public class ChatNotificationService {

    static final String NOTIFICATION_TYPE = "CHAT_MESSAGE";
    private static final int BODY_PREVIEW_LENGTH = 140;

    private final ChatRoomRepository roomRepository;
    private final NotificationService notificationService;

    public ChatNotificationService(ChatRoomRepository roomRepository,
                                   NotificationService notificationService) {
        this.roomRepository = roomRepository;
        this.notificationService = notificationService;
    }

    /**
     * Notify all active room members of a new message, except the sender and
     * members who muted the room. Dispatch is async inside NotificationService.
     */
    public void notifyNewMessage(ChatMessageResponse message, List<ChatRoomMembership> members) {
        List<String> recipientIds = members.stream()
                .filter(m -> !m.getUserId().equals(message.senderId()))
                .filter(m -> !Boolean.TRUE.equals(m.getMuted()))
                .map(ChatRoomMembership::getUserId)
                .toList();
        if (recipientIds.isEmpty()) {
            return;
        }

        ChatRoom room = roomRepository.findById(message.roomId()).orElse(null);
        String senderName = message.senderDisplayName();
        boolean useRoomTitle = room != null
                && room.getScope() != ChatRoomScope.DIRECT
                && room.getTitle() != null && !room.getTitle().isBlank();

        // Group rooms: "Room title" / "Sender: message". Direct: "Sender" / "message".
        String title = useRoomTitle ? room.getTitle() : senderName;
        String body = useRoomTitle ? senderName + ": " + preview(message.content()) : preview(message.content());

        Map<String, String> data = new HashMap<>();
        data.put("type", NOTIFICATION_TYPE);
        data.put("roomId", message.roomId());
        if (room != null && room.getClubId() != null) {
            data.put("clubId", room.getClubId());
        }
        notificationService.sendToUsers(recipientIds, title, body, data);
    }

    private String preview(String content) {
        if (content == null) {
            return "";
        }
        return content.length() > BODY_PREVIEW_LENGTH
                ? content.substring(0, BODY_PREVIEW_LENGTH) + "…"
                : content;
    }
}
