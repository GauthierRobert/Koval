package com.koval.trainingplannerbackend.ai;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

/**
 * Stateless, lightweight AI coaching during live workout sessions.
 * Each request is independent — no conversation memory, no tools.
 */
@Service
public class LiveCoachingService {

    private final ChatClient coachingClient;

    public LiveCoachingService(@Qualifier("liveCoachingClient") ChatClient coachingClient) {
        this.coachingClient = coachingClient;
    }

    public Flux<ServerSentEvent<String>> coach(CoachingRequest request) {
        String userMessage = buildCompactPrompt(request);
        return coachingClient.prompt()
                .user(userMessage)
                .stream()
                .content()
                .filter(token -> token != null && !token.isEmpty())
                .map(token -> ServerSentEvent.<String>builder()
                        .event("coaching")
                        .data(token)
                        .build())
                .concatWith(Flux.just(ServerSentEvent.<String>builder()
                        .event("done")
                        .data("")
                        .build()))
                .onErrorResume(ex -> Flux.just(ServerSentEvent.<String>builder()
                        .event("error")
                        .data("Coaching unavailable")
                        .build()));
    }

    private String buildCompactPrompt(CoachingRequest r) {
        var sb = new StringBuilder();
        sb.append("sport=").append(r.sportType()).append(" ftp=").append(r.ftp()).append('\n');
        sb.append("block=").append(r.blockType()).append(' ').append(r.blockIndex() + 1)
                .append('/').append(r.totalBlocks());
        if (r.blockLabel() != null) sb.append(" \"").append(r.blockLabel()).append('"');
        sb.append(" target=").append(r.targetIntensityPercent()).append('%');
        sb.append(" remaining=").append(r.remainingSeconds()).append("s");
        sb.append(" duration=").append(r.blockDurationSeconds()).append("s\n");
        sb.append("last15s: power=").append(r.avgPower())
                .append(" cad=").append(r.avgCadence())
                .append(" hr=").append(r.avgHeartRate()).append('\n');
        sb.append("block_avg: power=").append(r.blockAvgPower())
                .append(" cad=").append(r.blockAvgCadence())
                .append(" hr=").append(r.blockAvgHR()).append('\n');
        sb.append("session_avg: power=").append(r.sessionAvgPower())
                .append(" hr=").append(r.sessionAvgHR()).append('\n');
        sb.append("trigger=").append(r.triggerType());
        return sb.toString();
    }

    public record CoachingRequest(
            String sportType,
            int ftp,
            String blockType,
            String blockLabel,
            int blockIndex,
            int totalBlocks,
            int targetIntensityPercent,
            int remainingSeconds,
            int blockDurationSeconds,
            int avgPower,
            int avgCadence,
            int avgHeartRate,
            int blockAvgPower,
            int blockAvgCadence,
            int blockAvgHR,
            int sessionAvgPower,
            int sessionAvgHR,
            String triggerType
    ) {}
}
