package com.koval.trainingplannerbackend.chat;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executor;

/**
 * Per-user SSE fan-out for chat events.
 *
 * Keyed by {@code userId} (not roomId), so a single connection covers every room
 * a user is in — the client does not need to re-subscribe when switching rooms,
 * and we avoid an N-connections-per-user multiplier.
 *
 * A scheduled heartbeat every 30 seconds keeps connections alive across proxies
 * and detects stale emitters early (the send fails → emitter is cleaned up).
 *
 * Follows the {@code ClubFeedSseBroadcaster} pattern. Broker support is deferred;
 * for MVP we fan out locally only.
 */
@Component
public class ChatSseBroadcaster {

    private static final Logger log = LoggerFactory.getLogger(ChatSseBroadcaster.class);

    /** Cap how long a single emitter can stay open. Clients reconnect after this. */
    private static final long EMITTER_TIMEOUT_MS = Duration.ofMinutes(30).toMillis();

    private final ConcurrentHashMap<String, CopyOnWriteArrayList<SseEmitter>> emitters = new ConcurrentHashMap<>();
    private final ObjectMapper objectMapper;
    private final Executor sseExecutor;

    public ChatSseBroadcaster(ObjectMapper objectMapper,
                              @Qualifier("sseExecutor") Executor sseExecutor) {
        this.objectMapper = objectMapper;
        this.sseExecutor = sseExecutor;
    }

    public SseEmitter register(String userId) {
        SseEmitter emitter = new SseEmitter(EMITTER_TIMEOUT_MS);
        emitters.computeIfAbsent(userId, k -> new CopyOnWriteArrayList<>()).add(emitter);
        emitter.onCompletion(() -> removeEmitter(userId, emitter));
        emitter.onTimeout(() -> removeEmitter(userId, emitter));
        emitter.onError(e -> removeEmitter(userId, emitter));

        try {
            emitter.send(SseEmitter.event().name("connected").data("ok"));
        } catch (Exception e) {
            removeEmitter(userId, emitter);
        }
        return emitter;
    }

    /** Serialize and push an event to all of the user's active connections. */
    public void broadcast(String userId, String eventName, Object payload) {
        var list = emitters.get(userId);
        if (list == null || list.isEmpty()) return;

        String json;
        try {
            json = objectMapper.writeValueAsString(payload);
        } catch (Exception e) {
            log.error("Failed to serialize chat SSE payload: {}", e.getMessage());
            return;
        }

        for (SseEmitter emitter : list) {
            sseExecutor.execute(() -> sendOrDrop(userId, emitter, eventName, json));
        }
    }

    /**
     * Send a lightweight heartbeat to every connected emitter every 30 seconds.
     * Proxies/load-balancers often close idle connections; the heartbeat keeps
     * them alive. Failed sends trigger cleanup of stale emitters.
     *
     * Sends run on the {@code sseExecutor} so a single broken socket can't
     * raise an exception out of the scheduler thread (which is what was
     * surfacing client-abort IOExceptions to the global exception handler).
     */
    @Scheduled(fixedRate = 30_000)
    public void heartbeat() {
        emitters.forEach((userId, list) -> {
            if (list.isEmpty()) return;
            for (SseEmitter emitter : list) {
                sseExecutor.execute(() -> sendOrDrop(userId, emitter, "heartbeat", ""));
            }
        });
    }

    private void sendOrDrop(String userId, SseEmitter emitter, String eventName, String data) {
        try {
            emitter.send(SseEmitter.event().name(eventName).data(data));
        } catch (Exception e) {
            // Closed connection, slow client that filled the buffer, etc.
            removeEmitter(userId, emitter);
            try {
                emitter.completeWithError(e);
            } catch (Exception ignored) {
                // Emitter may already be completed.
            }
        }
    }

    private void removeEmitter(String userId, SseEmitter emitter) {
        var list = emitters.get(userId);
        if (list != null) {
            list.remove(emitter);
            if (list.isEmpty()) {
                emitters.remove(userId);
            }
        }
    }
}
