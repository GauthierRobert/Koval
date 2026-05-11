package com.koval.trainingplannerbackend.club.feed;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executor;

@Component
public class ClubFeedSseBroadcaster {

    private static final Logger log = LoggerFactory.getLogger(ClubFeedSseBroadcaster.class);

    /** Cap how long a single emitter can stay open. Clients reconnect after this. */
    private static final long EMITTER_TIMEOUT_MS = Duration.ofMinutes(30).toMillis();

    private final ConcurrentHashMap<String, CopyOnWriteArrayList<SseEmitter>> emitters = new ConcurrentHashMap<>();
    private final ObjectMapper objectMapper;
    private final Optional<ClubFeedBrokerPublisher> brokerPublisher;
    private final Executor sseExecutor;

    public ClubFeedSseBroadcaster(ObjectMapper objectMapper,
                                  Optional<ClubFeedBrokerPublisher> brokerPublisher,
                                  @Qualifier("sseExecutor") Executor sseExecutor) {
        this.objectMapper = objectMapper;
        this.brokerPublisher = brokerPublisher;
        this.sseExecutor = sseExecutor;
    }

    public SseEmitter register(String clubId) {
        SseEmitter emitter = new SseEmitter(EMITTER_TIMEOUT_MS);
        emitters.computeIfAbsent(clubId, k -> new CopyOnWriteArrayList<>()).add(emitter);
        emitter.onCompletion(() -> removeEmitter(clubId, emitter));
        emitter.onTimeout(() -> removeEmitter(clubId, emitter));
        emitter.onError(e -> removeEmitter(clubId, emitter));

        // Send initial heartbeat so the client knows the connection is alive
        try {
            emitter.send(SseEmitter.event().name("connected").data("ok"));
        } catch (Exception e) {
            removeEmitter(clubId, emitter);
        }

        return emitter;
    }

    /**
     * Serialize payload and publish: via broker if available, or directly to local emitters.
     */
    public void broadcast(String clubId, String eventName, Object payload) {
        String json;
        try {
            json = objectMapper.writeValueAsString(payload);
        } catch (Exception e) {
            log.error("Failed to serialize SSE payload: {}", e.getMessage());
            return;
        }

        if (brokerPublisher.isPresent()) {
            brokerPublisher.get().publish(new ClubFeedBroadcastMessage(clubId, eventName, json));
        } else {
            broadcastLocal(clubId, eventName, json);
        }
    }

    /**
     * Push a pre-serialized JSON payload to all local SSE emitters for a club.
     * Each emitter's send happens on the {@code sseExecutor} so a single slow or
     * stuck client cannot block the rest of the fan-out (or the caller thread).
     */
    public void broadcastLocal(String clubId, String eventName, String json) {
        var list = emitters.get(clubId);
        if (list == null || list.isEmpty()) return;

        for (SseEmitter emitter : list) {
            sseExecutor.execute(() -> {
                try {
                    emitter.send(SseEmitter.event().name(eventName).data(json));
                } catch (Exception e) {
                    // Drop the emitter on any send failure (closed connection, slow client
                    // that filled the response buffer, etc.). onError is fired by SseEmitter
                    // itself when we complete it with an exception.
                    removeEmitter(clubId, emitter);
                    try {
                        emitter.completeWithError(e);
                    } catch (Exception ignored) {
                        // Emitter may already be completed.
                    }
                }
            });
        }
    }

    private void removeEmitter(String clubId, SseEmitter emitter) {
        var list = emitters.get(clubId);
        if (list != null) {
            list.remove(emitter);
            if (list.isEmpty()) {
                emitters.remove(clubId);
            }
        }
    }
}
