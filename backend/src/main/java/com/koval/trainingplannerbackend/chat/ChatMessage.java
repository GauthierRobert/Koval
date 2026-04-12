package com.koval.trainingplannerbackend.chat;

import lombok.Getter;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

@Getter
@Setter
@Document(collection = "chat_messages")
@CompoundIndexes({
    @CompoundIndex(name = "roomId_createdAt", def = "{'roomId': 1, 'createdAt': -1}"),
    @CompoundIndex(name = "senderId_createdAt", def = "{'senderId': 1, 'createdAt': -1}")
})
public class ChatMessage {

    @Id
    private String id;

    @Indexed
    private String roomId;

    private String senderId;
    private String content;
    private Instant createdAt;
    private Instant editedAt;
    private boolean deleted;

    /** Optional idempotency key so retries from the client don't duplicate messages. */
    private String clientNonce;

    private ChatMessageType type;
}
