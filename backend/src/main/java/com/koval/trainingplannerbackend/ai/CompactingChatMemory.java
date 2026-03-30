package com.koval.trainingplannerbackend.ai;

import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;

import java.util.ArrayList;
import java.util.List;

/**
 * Wraps a ChatMemory to compact older messages before they are sent to the AI.
 * <p>
 * The last {@code KEEP_FULL} messages are returned intact; older messages are
 * truncated to {@code MAX_COMPACT_LENGTH} characters so that a larger window
 * can fit in the token budget.
 * <p>
 * Use {@link #getFullMessages(String)} for the uncompacted originals (e.g. when
 * returning conversation history to the frontend).
 */
public class CompactingChatMemory implements ChatMemory {

    private static final int KEEP_FULL = 4;
    private static final int MAX_COMPACT_LENGTH = 200;

    private final ChatMemory delegate;

    public CompactingChatMemory(ChatMemory delegate) {
        this.delegate = delegate;
    }

    @Override
    public void add(String conversationId, List<Message> messages) {
        delegate.add(conversationId, messages);
    }

    /**
     * Returns messages with older entries compacted to 1-2 lines.
     * Used by {@link org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor}
     * when building the AI prompt.
     */
    @Override
    public List<Message> get(String conversationId) {
        List<Message> messages = delegate.get(conversationId);
        if (messages == null || messages.size() <= KEEP_FULL) {
            return messages;
        }

        List<Message> result = new ArrayList<>(messages.size());
        int compactUntil = messages.size() - KEEP_FULL;

        for (int i = 0; i < messages.size(); i++) {
            if (i < compactUntil) {
                result.add(compactMessage(messages.get(i)));
            } else {
                result.add(messages.get(i));
            }
        }
        return result;
    }

    /** Returns the full, uncompacted messages — for frontend display. */
    public List<Message> getFullMessages(String conversationId) {
        return delegate.get(conversationId);
    }

    @Override
    public void clear(String conversationId) {
        delegate.clear(conversationId);
    }

    private Message compactMessage(Message message) {
        String text = message.getText();
        if (text == null || text.length() <= MAX_COMPACT_LENGTH) {
            return message;
        }
        String compacted = text.substring(0, MAX_COMPACT_LENGTH) + "…";
        return switch (message.getMessageType()) {
            case USER -> new UserMessage(compacted);
            case ASSISTANT -> new AssistantMessage(compacted);
            default -> message;
        };
    }
}
