package com.koval.trainingplannerbackend.ai;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.messages.Message;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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
    private static final int SUMMARY_CACHE_MAX_ENTRIES = 1000;

    private static final String SUMMARIZE_PROMPT = """
            Summarize this conversation history in 2-3 concise sentences.
            Focus on: key decisions made, workouts created (titles, types),
            user preferences mentioned, and any outstanding requests.
            Return ONLY the summary, no preamble.""";

    private final ChatClient routerClient;
    private final ChatMemory chatMemory;
    private final ChatHistoryRepository chatHistoryRepository;

    /**
     * Bounded LRU cache (access-order, eldest-eviction) so summary lookups stay fast for hot
     * conversations without leaking memory across the server lifetime.
     */
    private final Map<String, String> summaryCache = Collections.synchronizedMap(
            new LinkedHashMap<>(SUMMARY_CACHE_MAX_ENTRIES + 1, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<String, String> eldest) {
                    return size() > SUMMARY_CACHE_MAX_ENTRIES;
                }
            });

    public ConversationSummarizer(@Qualifier("routerClient") ChatClient routerClient,
                                   ChatMemory chatMemory,
                                   ChatHistoryRepository chatHistoryRepository) {
        this.routerClient = routerClient;
        this.chatMemory = chatMemory;
        this.chatHistoryRepository = chatHistoryRepository;
    }

    /**
     * Returns a previously generated conversation summary, populating the local cache from
     * persistent storage on first hit. Returns {@code null} when no summary has been generated.
     */
    public String getSummaryIfNeeded(String conversationId) {
        String cached = summaryCache.get(conversationId);
        if (cached != null) return cached;

        String stored = chatHistoryRepository.findById(conversationId)
                .map(ChatHistory::getConversationSummary)
                .filter(s -> s != null && !s.isBlank())
                .orElse(null);
        if (stored != null) {
            summaryCache.put(conversationId, stored);
        }
        return stored;
    }

    /**
     * Generates and stores a summary for conversations approaching the memory window limit.
     * Called after responses when message count exceeds the threshold.
     */
    public void summarizeIfNeeded(String conversationId) {
        if (summaryCache.containsKey(conversationId)) {
            return;
        }
        List<Message> messages = chatMemory.get(conversationId);
        if (messages == null || messages.size() < SUMMARIZE_THRESHOLD) {
            return;
        }

        try {
            int halfPoint = messages.size() / 2;
            List<Message> olderMessages = messages.subList(0, halfPoint);

            String conversationText = olderMessages.stream()
                    .map(m -> m.getMessageType().name() + ": " + m.getText())
                    .collect(Collectors.joining("\n"));

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
        } catch (Exception e) {
            log.warn("Failed to generate conversation summary for {}: {}", conversationId, e.getMessage());
        }
    }
}
