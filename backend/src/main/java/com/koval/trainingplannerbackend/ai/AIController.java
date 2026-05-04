package com.koval.trainingplannerbackend.ai;

import com.koval.trainingplannerbackend.ai.agents.AgentType;
import com.koval.trainingplannerbackend.auth.SecurityUtils;
import com.koval.trainingplannerbackend.config.audit.AuditLog;
import com.koval.trainingplannerbackend.config.exceptions.RateLimitException;
import com.koval.trainingplannerbackend.config.exceptions.ValidationException;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.ai.chat.messages.Message;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;

import java.util.List;
import java.util.Optional;

@RestController
@RequestMapping("/api/ai")
public class AIController {

    /** Hard limit on user message length to stay within TPM budget. */
    private static final int MAX_MESSAGE_CHARS = 8_000;

    private final AIService aiService;
    private final ChatHistoryService chatHistoryService;
    private final AiRateLimiter rateLimiter;

    public AIController(AIService aiService, ChatHistoryService chatHistoryService, AiRateLimiter rateLimiter) {
        this.aiService = aiService;
        this.chatHistoryService = chatHistoryService;
        this.rateLimiter = rateLimiter;
    }

    // ── Chat ────────────────────────────────────────────────────────────

    @AuditLog(action = "AI_CHAT")
    @PostMapping("/chat")
    public ResponseEntity<?> chat(@RequestBody ChatRequest request) {
        String userId = checkRateLimitedUser(request.message());
        AgentType agentType = parseAgentType(request.agentType());
        try {
            return ResponseEntity.ok(aiService.chat(request.message(), userId, request.chatHistoryId(), agentType));
        } catch (RuntimeException ex) {
            throw toApiException(ex);
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
        rateLimiter.checkLimit(userId);
        AgentType agentType = parseAgentType(request.agentType());
        var streamResponse = aiService.chatStream(msg, userId, request.chatHistoryId(), agentType);
        response.setHeader("X-Chat-History-Id", streamResponse.chatHistoryId());
        return streamResponse.events();
    }

    // ── Planner ─────────────────────────────────────────────────────────

    @AuditLog(action = "AI_PLAN")
    @PostMapping("/plan")
    public ResponseEntity<?> plan(@RequestBody ChatRequest request) {
        checkRateLimitedUser(request.message());
        try {
            return ResponseEntity.ok(aiService.plan(request.message()));
        } catch (RuntimeException ex) {
            throw toApiException(ex);
        }
    }

    // ── History ─────────────────────────────────────────────────────────

    @GetMapping("/history")
    public ResponseEntity<List<ChatHistory>> getUserHistory(
            @RequestParam(defaultValue = "30") int limit,
            @RequestParam(defaultValue = "0") int page) {
        String userId = SecurityUtils.getCurrentUserId();
        // Capped to keep the sidebar load bounded; clients needing more must page.
        int safeLimit = Math.min(Math.max(limit, 1), 100);
        return ResponseEntity.ok(chatHistoryService.findByUser(userId, PageRequest.of(page, safeLimit)));
    }

    @GetMapping("/history/{chatHistoryId}")
    public ResponseEntity<ChatHistoryDetail> getChatHistory(@PathVariable String chatHistoryId) {
        String userId = SecurityUtils.getCurrentUserId();
        ChatHistory metadata = chatHistoryService.findByIdForUser(chatHistoryId, userId);
        List<Message> messages = chatHistoryService.getMessages(chatHistoryId);

        List<ConversationMessage> conversationMessages = messages.stream()
                .map(m -> new ConversationMessage(
                        m.getMessageType().name().toLowerCase(),
                        m.getText()))
                .toList();

        return ResponseEntity.ok(new ChatHistoryDetail(metadata, conversationMessages));
    }

    @DeleteMapping("/history/{chatHistoryId}")
    public ResponseEntity<Void> deleteChatHistory(@PathVariable String chatHistoryId) {
        String userId = SecurityUtils.getCurrentUserId();
        chatHistoryService.deleteForUser(chatHistoryId, userId);
        return ResponseEntity.noContent().build();
    }

    // ── DTOs ──────────────────────────────────────────────────

    public record ChatRequest(String message, String chatHistoryId, String agentType) {}
    public record ConversationMessage(String role, String content) {}
    public record ChatHistoryDetail(ChatHistory metadata, List<ConversationMessage> messages) {}

    // ── Helpers ─────────────────────────────────────────────────────────

    /** Validates the message, resolves the current user, applies rate limiting, and returns the userId. */
    private String checkRateLimitedUser(String message) {
        validateMessage(message);
        String userId = SecurityUtils.getCurrentUserId();
        rateLimiter.checkLimit(userId);
        return userId;
    }

    private void validateMessage(String msg) {
        if (msg == null || msg.isBlank()) {
            throw new ValidationException("Message cannot be empty.", "EMPTY_MESSAGE");
        }
        if (msg.length() > MAX_MESSAGE_CHARS) {
            throw new ValidationException(
                    "Your message is too long (" + msg.length() + " chars). Please keep it under "
                            + MAX_MESSAGE_CHARS + " characters and try again.",
                    "MESSAGE_TOO_LONG");
        }
    }

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

    private RuntimeException toApiException(RuntimeException ex) {
        String msg = Optional.ofNullable(ex.getMessage()).orElse("");
        if (msg.contains("429") || msg.contains("rate_limit")) {
            return new RateLimitException(
                    "Your request was too large or you've sent too many requests this minute. "
                            + "Please shorten your message or wait a moment and try again.");
        }
        return ex;
    }
}
