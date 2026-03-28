package com.koval.trainingplannerbackend.club.feed;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

@Component
public class ClubFeedSseBroadcaster {

    private static final Logger log = LoggerFactory.getLogger(ClubFeedSseBroadcaster.class);

    private final ConcurrentHashMap<String, CopyOnWriteArrayList<SseEmitter>> emitters = new ConcurrentHashMap<>();
    private final ObjectMapper objectMapper;
    private final Optional<ClubFeedBrokerPublisher> brokerPublisher;

    public ClubFeedSseBroadcaster(ObjectMapper objectMapper,
                                  Optional<ClubFeedBrokerPublisher> brokerPublisher) {
        this.objectMapper = objectMapper;
        this.brokerPublisher = brokerPublisher;
    }

    public SseEmitter register(String clubId) {
        SseEmitter emitter = new SseEmitter(0L); // no timeout
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
     * Called directly (single-instance fallback) or by the broker consumer.
     */
    public void broadcastLocal(String clubId, String eventName, String json) {
        var list = emitters.get(clubId);
        if (list == null || list.isEmpty()) return;

        List<SseEmitter> dead = new ArrayList<>();
        for (SseEmitter emitter : list) {
            try {
                emitter.send(SseEmitter.event().name(eventName).data(json));
            } catch (Exception e) {
                dead.add(emitter);
            }
        }
        dead.forEach(e -> removeEmitter(clubId, e));
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
