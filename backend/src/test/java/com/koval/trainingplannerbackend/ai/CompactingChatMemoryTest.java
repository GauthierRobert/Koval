package com.koval.trainingplannerbackend.ai;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class CompactingChatMemoryTest {

    private InMemoryChatMemory delegate;
    private CompactingChatMemory memory;

    @BeforeEach
    void setUp() {
        delegate = new InMemoryChatMemory();
        memory = new CompactingChatMemory(delegate);
    }

    private static String repeat(String s, int times) {
        return s.repeat(times);
    }

    @Nested
    class ShortConversations {

        @Test
        void empty_returnsEmpty() {
            List<Message> result = memory.get("conv-1");
            assertNotNull(result);
            assertTrue(result.isEmpty());
        }

        @Test
        void fewerThanKeepFull_returnsAllMessagesUntouched() {
            String long1 = repeat("a", 500);
            String long2 = repeat("b", 500);
            memory.add("conv-1", List.of(new UserMessage(long1), new AssistantMessage(long2)));

            List<Message> result = memory.get("conv-1");

            assertEquals(2, result.size());
            assertEquals(long1, result.get(0).getText());
            assertEquals(long2, result.get(1).getText());
        }
    }

    @Nested
    class LongConversations {

        @Test
        void olderMessages_compactedTo200CharsPlusEllipsis() {
            String longText = repeat("x", 500);
            // 6 messages: oldest 2 should be compacted, newest 4 kept full
            List<Message> messages = new ArrayList<>();
            for (int i = 0; i < 6; i++) {
                messages.add(i % 2 == 0 ? new UserMessage(longText) : new AssistantMessage(longText));
            }
            memory.add("conv-1", messages);

            List<Message> result = memory.get("conv-1");

            assertEquals(6, result.size());
            // First 2 compacted (200 chars + ellipsis)
            assertEquals(201, result.get(0).getText().length());
            assertTrue(result.get(0).getText().endsWith("…"));
            assertEquals(201, result.get(1).getText().length());
            // Last 4 kept full
            for (int i = 2; i < 6; i++) {
                assertEquals(500, result.get(i).getText().length());
            }
        }

        @Test
        void shortOldMessages_notCompactedEvenIfOlder() {
            // Even though older, short messages already fit and shouldn't get an ellipsis appended.
            String shortText = "ok";
            String longText = repeat("z", 500);
            List<Message> messages = new ArrayList<>();
            messages.add(new UserMessage(shortText));   // short, older
            for (int i = 0; i < 4; i++) {
                messages.add(new AssistantMessage(longText));
            }
            memory.add("conv-1", messages);

            List<Message> result = memory.get("conv-1");

            assertEquals(5, result.size());
            assertEquals(shortText, result.get(0).getText(), "short older messages stay intact");
        }

        @Test
        void compactPreservesMessageType() {
            String longText = repeat("k", 400);
            List<Message> messages = List.of(
                    new UserMessage(longText),
                    new AssistantMessage(longText),
                    new UserMessage(longText),
                    new AssistantMessage(longText),
                    new UserMessage(longText));
            memory.add("conv-1", messages);

            List<Message> result = memory.get("conv-1");

            assertEquals(5, result.size());
            // Index 0 is older — should be compacted but still UserMessage
            assertTrue(result.get(0) instanceof UserMessage);
            assertEquals(201, result.get(0).getText().length());
        }

        @Test
        void getFullMessages_alwaysReturnsUncompacted() {
            String longText = repeat("a", 500);
            List<Message> messages = new ArrayList<>();
            for (int i = 0; i < 8; i++) {
                messages.add(new UserMessage(longText));
            }
            memory.add("conv-1", messages);

            List<Message> full = memory.getFullMessages("conv-1");

            assertEquals(8, full.size());
            for (Message m : full) {
                assertEquals(500, m.getText().length());
            }
        }
    }

    @Nested
    class Delegation {

        @Test
        void clear_delegatesToUnderlying() {
            memory.add("conv-1", List.of(new UserMessage("hi")));
            assertEquals(1, memory.get("conv-1").size());

            memory.clear("conv-1");

            assertTrue(memory.get("conv-1").isEmpty());
        }

        @Test
        void add_appendsRatherThanReplaces() {
            memory.add("conv-1", List.of(new UserMessage("hi")));
            memory.add("conv-1", List.of(new AssistantMessage("hello")));

            assertEquals(2, memory.get("conv-1").size());
        }
    }

    /**
     * Minimal in-memory ChatMemory used as a delegate for unit tests.
     */
    private static class InMemoryChatMemory implements ChatMemory {
        private final Map<String, List<Message>> store = new HashMap<>();

        @Override
        public void add(String conversationId, List<Message> messages) {
            store.computeIfAbsent(conversationId, k -> new ArrayList<>()).addAll(messages);
        }

        @Override
        public List<Message> get(String conversationId) {
            return new ArrayList<>(store.getOrDefault(conversationId, List.of()));
        }

        @Override
        public void clear(String conversationId) {
            store.remove(conversationId);
        }
    }

}
