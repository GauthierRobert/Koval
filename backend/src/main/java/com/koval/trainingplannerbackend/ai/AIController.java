package com.koval.trainingplannerbackend.ai;

import jakarta.servlet.http.HttpServletResponse;
import org.springframework.ai.chat.messages.Message;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;

import java.util.List;

@RestController
@RequestMapping("/api/ai")
@CrossOrigin(origins = "*", exposedHeaders = "X-Chat-History-Id")
public class AIController {

    private final AIService aiService;
    private final ChatHistoryService chatHistoryService;

    public AIController(AIService aiService, ChatHistoryService chatHistoryService) {
        this.aiService = aiService;
        this.chatHistoryService = chatHistoryService;
    }

    // ── Chat ────────────────────────────────────────────────────────────

    @PostMapping("/chat")
    public ResponseEntity<AIService.ChatMessageResponse> chat(
            @RequestBody ChatRequest request,
            @RequestHeader(value = "X-User-Id", required = false) String headerUserId) {

        String userId = resolveUserId(headerUserId, request.userId());
        var response = aiService.chat(request.message(), userId, request.chatHistoryId());
        return ResponseEntity.ok(response);
    }

    @PostMapping(value = "/chat/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<String>> chatStream(
            @RequestBody ChatRequest request,
            @RequestHeader(value = "X-User-Id", required = false) String headerUserId,
            HttpServletResponse response) {

        String userId = resolveUserId(headerUserId, request.userId());
        var streamResponse = aiService.chatStream(request.message(), userId, request.chatHistoryId());
        response.setHeader("X-Chat-History-Id", streamResponse.chatHistoryId());
        return streamResponse.events();
    }

    // ── History ─────────────────────────────────────────────────────────

    @GetMapping("/history")
    public ResponseEntity<List<ChatHistory>> getUserHistory(
            @RequestHeader(value = "X-User-Id", required = false) String userId,
            @RequestParam(required = false) String userIdParam) {

        String finalUserId = userId != null ? userId : userIdParam;
        if (finalUserId == null) {
            return ResponseEntity.badRequest().build();
        }
        return ResponseEntity.ok(chatHistoryService.findByUser(finalUserId));
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

    // ── Helpers & DTOs ──────────────────────────────────────────────────

    private String resolveUserId(String headerUserId, String requestUserId) {
        if (headerUserId != null) return headerUserId;
        if (requestUserId != null) return requestUserId;
        return "anonymous";
    }

    public record ChatRequest(String message, String chatHistoryId, String userId) {}
    public record ConversationMessage(String role, String content) {}
    public record ChatHistoryDetail(ChatHistory metadata, List<ConversationMessage> messages) {}
}
