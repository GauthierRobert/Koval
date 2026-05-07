package com.koval.trainingplannerbackend.ai;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.messages.Message;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Summarizes older conversation messages to preserve context when the
 * message window is full. Uses a lightweight Haiku call to compress
 * the oldest messages into a compact summary paragraph.
 */
@Component
public class ConversationSummarizer {

    private static final Logger log = LoggerFactory.getLogger(ConversationSummarizer.class);
    private static final int SUMMARIZE_THRESHOLD = 10;

    private static final String SUMMARIZE_PROMPT = """
            Summarize this conversation history in 2-3 concise sentences.
            Focus on: key decisions made, workouts created (titles, types),
            user preferences mentioned, and any outstanding requests.
            Return ONLY the summary, no preamble.""";

    private final ChatClient routerClient;
    private final ChatMemory chatMemory;
    private final ChatHistoryRepository chatHistoryRepository;
    private final Map<String, String> summaryCache = new ConcurrentHashMap<>();

    public ConversationSummarizer(@Qualifier("routerClient") ChatClient routerClient,
                                   ChatMemory chatMemory,
                                   ChatHistoryRepository chatHistoryRepository) {
        this.routerClient = routerClient;
        this.chatMemory = chatMemory;
        this.chatHistoryRepository = chatHistoryRepository;
    }

    /**
     * Returns a conversation summary if the conversation is long enough to warrant one.
     * Returns null for short conversations.
     */
    public String getSummaryIfNeeded(String conversationId) {
        String cached = summaryCache.get(conversationId);
        if (cached != null) return cached;

        List<Message> messages = chatMemory.get(conversationId);
        if (messages == null || messages.size() < SUMMARIZE_THRESHOLD) {
            return null;
        }

        return chatHistoryRepository.findById(conversationId)
                .map(ChatHistory::getConversationSummary)
                .orElse(null);
    }

    /**
     * Generates and stores a summary for conversations approaching the memory window limit.
     * Called after responses when message count exceeds the threshold.
     */
    public void summarizeIfNeeded(String conversationId) {
        Optional.ofNullable(chatMemory.get(conversationId))
                .filter(msgs -> msgs.size() >= SUMMARIZE_THRESHOLD)
                .filter(msgs -> !summaryCache.containsKey(conversationId))
                .ifPresent(msgs -> doSummarize(conversationId, msgs));
    }

    private void doSummarize(String conversationId, List<Message> messages) {
        List<Message> olderMessages = messages.subList(0, messages.size() / 2);
        String conversationText = olderMessages.stream()
                .map(m -> m.getMessageType().name() + ": " + m.getText())
                .collect(Collectors.joining("\n"));
        try {
            String summary = routerClient.prompt()
                    .system(SUMMARIZE_PROMPT)
                    .user(conversationText)
                    .call()
                    .content();
            summaryCache.put(conversationId, summary);
            chatHistoryRepository.findById(conversationId).ifPresent(history -> {
                history.setConversationSummary(summary);
                chatHistoryRepository.save(history);
            });
            log.debug("Generated conversation summary for {}: {} chars", conversationId, summary.length());
        } catch (RuntimeException e) {
            log.warn("Failed to generate conversation summary for {} ({}): {}",
                    conversationId, e.getClass().getSimpleName(), e.getMessage());
        }
    }
}
