package com.koval.trainingplannerbackend.ai;

import com.koval.trainingplannerbackend.ai.agents.AgentType;
import com.koval.trainingplannerbackend.auth.SecurityUtils;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.ai.chat.messages.Message;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/ai")
@CrossOrigin(origins = "*", exposedHeaders = "X-Chat-History-Id")
public class AIController {

    /** Hard limit on user message length to stay within TPM budget. */
    private static final int MAX_MESSAGE_CHARS = 8_000;

    private final AIService aiService;
    private final ChatHistoryService chatHistoryService;

    public AIController(AIService aiService, ChatHistoryService chatHistoryService) {
        this.aiService = aiService;
        this.chatHistoryService = chatHistoryService;
    }

    // ── Chat ────────────────────────────────────────────────────────────

    @PostMapping("/chat")
    public ResponseEntity<?> chat(@RequestBody ChatRequest request) {
        String msg = request.message();
        if (msg == null || msg.isBlank()) {
            return ResponseEntity.badRequest().body(error("empty_message", "Message cannot be empty."));
        }
        if (msg.length() > MAX_MESSAGE_CHARS) {
            return ResponseEntity.badRequest().body(error("message_too_long",
                    "Your message is too long (" + msg.length() + " chars). Please keep it under "
                            + MAX_MESSAGE_CHARS + " characters and try again."));
        }
        try {
            String userId = SecurityUtils.getCurrentUserId();
            AgentType agentType = parseAgentType(request.agentType());
            var response = aiService.chat(msg, userId, request.chatHistoryId(), agentType);
            return ResponseEntity.ok(response);
        } catch (RuntimeException ex) {
            return handleAiException(ex);
        }
    }

    @PostMapping(value = "/chat/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<String>> chatStream(
            @RequestBody ChatRequest request,
            HttpServletResponse response) {
        String msg = request.message();
        if (msg == null || msg.isBlank() || msg.length() > MAX_MESSAGE_CHARS) {
            String errMsg = (msg == null || msg.isBlank())
                    ? "Message cannot be empty."
                    : "Your message is too long. Please keep it under " + MAX_MESSAGE_CHARS + " characters.";
            return Flux.just(ServerSentEvent.<String>builder().event("error").data(errMsg).build());
        }
        String userId = SecurityUtils.getCurrentUserId();
        AgentType agentType = parseAgentType(request.agentType());
        var streamResponse = aiService.chatStream(msg, userId, request.chatHistoryId(), agentType);
        response.setHeader("X-Chat-History-Id", streamResponse.chatHistoryId());
        return streamResponse.events();
    }

    // ── History ─────────────────────────────────────────────────────────

    @GetMapping("/history")
    public ResponseEntity<List<ChatHistory>> getUserHistory() {
        String userId = SecurityUtils.getCurrentUserId();
        return ResponseEntity.ok(chatHistoryService.findByUser(userId));
    }

    @GetMapping("/history/{chatHistoryId}")
    public ResponseEntity<ChatHistoryDetail> getChatHistory(@PathVariable String chatHistoryId) {
        try {
            ChatHistory metadata = chatHistoryService.findById(chatHistoryId);
            List<Message> messages = chatHistoryService.getMessages(chatHistoryId);

            List<ConversationMessage> conversationMessages = messages.stream()
                    .map(m -> new ConversationMessage(
                            m.getMessageType().name().toLowerCase(),
                            m.getText()))
                    .toList();

            return ResponseEntity.ok(new ChatHistoryDetail(metadata, conversationMessages));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        }
    }

    @DeleteMapping("/history/{chatHistoryId}")
    public ResponseEntity<Void> deleteChatHistory(@PathVariable String chatHistoryId) {
        chatHistoryService.delete(chatHistoryId);
        return ResponseEntity.noContent().build();
    }

    // ── DTOs ──────────────────────────────────────────────────

    public record ChatRequest(String message, String chatHistoryId, String agentType) {}
    public record ConversationMessage(String role, String content) {}
    public record ChatHistoryDetail(ChatHistory metadata, List<ConversationMessage> messages) {}

    // ── Helpers ─────────────────────────────────────────────────────────

    private AgentType parseAgentType(String agentType) {
        if (agentType == null || agentType.isBlank()) {
            return null; // router will classify
        }
        try {
            return AgentType.valueOf(agentType.toUpperCase());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private ResponseEntity<Map<String, String>> handleAiException(RuntimeException ex) {
        String msg = ex.getMessage() != null ? ex.getMessage() : "";
        if (msg.contains("429") || msg.contains("rate_limit")) {
            return ResponseEntity.status(429).body(error("rate_limit_exceeded",
                    "Your request was too large or you've sent too many requests this minute. "
                            + "Please shorten your message or wait a moment and try again."));
        }
        throw ex;
    }

    private Map<String, String> error(String code, String message) {
        return Map.of("error", code, "message", message);
    }
}
