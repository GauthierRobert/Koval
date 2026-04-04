package com.koval.trainingplannerbackend.ai.anonymization;

import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages per-conversation {@link AnonymizationContext} instances.
 * Each conversation gets its own mapping so aliases are stable within a chat
 * but cannot be correlated across conversations.
 */
@Service
public class AnonymizationService {

    public static final String ANONYMIZATION_CTX_KEY = "anonymizationContext";

    private final Map<String, AnonymizationContext> contexts = new ConcurrentHashMap<>();

    /**
     * Returns (or lazily creates) the anonymization context for a conversation.
     */
    public AnonymizationContext getOrCreate(String conversationId) {
        return contexts.computeIfAbsent(conversationId, k -> new AnonymizationContext());
    }

    /**
     * Removes the context when a conversation is deleted.
     */
    public void remove(String conversationId) {
        contexts.remove(conversationId);
    }

    /**
     * Extracts the {@link AnonymizationContext} from a Spring AI ToolContext map.
     * Returns {@code null} if not present.
     */
    public static AnonymizationContext fromToolContext(Map<String, Object> toolContext) {
        Object ctx = toolContext.get(ANONYMIZATION_CTX_KEY);
        return ctx instanceof AnonymizationContext ac ? ac : null;
    }
}
