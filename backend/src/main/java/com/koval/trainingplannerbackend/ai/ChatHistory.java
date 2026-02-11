package com.koval.trainingplannerbackend.ai;

import lombok.Getter;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;

/**
 * Lightweight metadata record for tracking conversations per user.
 * Actual messages are stored by Spring AI's MongoChatMemoryRepository.
 */
@Setter
@Getter
@Document(collection = "chat_history")
public class ChatHistory {
    @Id
    private String id;
    private String userId;
    private String title;
    private LocalDateTime startedAt;
    private LocalDateTime lastUpdatedAt;

    public ChatHistory() {
    }

}
