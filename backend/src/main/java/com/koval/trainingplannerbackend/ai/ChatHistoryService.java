package com.koval.trainingplannerbackend.ai;

import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.messages.Message;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Manages chat history metadata and conversation persistence.
 * ChatHistory is lightweight metadata; actual messages are stored by Spring AI's ChatMemory.
 */
@Service
public class ChatHistoryService {

    private final ChatHistoryRepository chatHistoryRepository;
    private final ChatMemory chatMemory;

    public ChatHistoryService(ChatHistoryRepository chatHistoryRepository, ChatMemory chatMemory) {
        this.chatHistoryRepository = chatHistoryRepository;
        this.chatMemory = chatMemory;
    }

    public ChatHistory findOrCreate(String userId, String chatHistoryId) {
        if (chatHistoryId != null && !chatHistoryId.isEmpty()) {
            return chatHistoryRepository.findById(chatHistoryId)
                    .orElseGet(() -> create(userId));
        }
        return create(userId);
    }

    public ChatHistory create(String userId) {
        ChatHistory chatHistory = new ChatHistory();
        chatHistory.setId(UUID.randomUUID().toString());
        chatHistory.setUserId(userId);
        chatHistory.setStartedAt(LocalDateTime.now());
        chatHistory.setLastUpdatedAt(LocalDateTime.now());
        return chatHistoryRepository.save(chatHistory);
    }

    public void updateAfterResponse(ChatHistory chatHistory, String userMessage) {
        if (chatHistory.getTitle() == null) {
            chatHistory.setTitle(truncate(userMessage, 80));
        }
        chatHistory.setLastUpdatedAt(LocalDateTime.now());
        chatHistoryRepository.save(chatHistory);
    }

    public List<ChatHistory> findByUser(String userId) {
        return chatHistoryRepository.findByUserId(userId);
    }

    public ChatHistory findById(String chatHistoryId) {
        return chatHistoryRepository.findById(chatHistoryId)
                .orElseThrow(() -> new IllegalArgumentException("Chat history not found: " + chatHistoryId));
    }

    public List<Message> getMessages(String conversationId) {
        return chatMemory.get(conversationId);
    }

    public void delete(String chatHistoryId) {
        chatMemory.clear(chatHistoryId);
        chatHistoryRepository.deleteById(chatHistoryId);
    }

    private String truncate(String text, int maxLength) {
        return text.length() > maxLength ? text.substring(0, maxLength) + "..." : text;
    }
}
