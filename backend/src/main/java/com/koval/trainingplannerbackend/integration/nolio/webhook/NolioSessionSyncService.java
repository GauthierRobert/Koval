package com.koval.trainingplannerbackend.integration.nolio.webhook;

import com.fasterxml.jackson.databind.JsonNode;
import com.koval.trainingplannerbackend.auth.User;
import com.koval.trainingplannerbackend.auth.UserRepository;
import com.koval.trainingplannerbackend.integration.nolio.write.NolioApiClient;
import com.koval.trainingplannerbackend.training.history.CompletedSession;
import com.koval.trainingplannerbackend.training.history.CompletedSessionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.Optional;

/**
 * Handles the achieved-events webhook channel: keeps completed sessions in
 * step with Nolio (create on new_event, refresh on updated_event, remove on
 * deleted_event). Mirrors the Strava webhook flow: ack fast, process async,
 * re-fetch the object because the webhook payload is only a notification.
 */
@Service
public class NolioSessionSyncService {

    private static final Logger log = LoggerFactory.getLogger(NolioSessionSyncService.class);

    private final UserRepository userRepository;
    private final NolioApiClient apiClient;
    private final NolioSessionImportMapper mapper;
    private final NolioActivityIngestService ingestService;
    private final CompletedSessionRepository sessionRepository;

    public NolioSessionSyncService(UserRepository userRepository,
                                   NolioApiClient apiClient,
                                   NolioSessionImportMapper mapper,
                                   NolioActivityIngestService ingestService,
                                   CompletedSessionRepository sessionRepository) {
        this.userRepository = userRepository;
        this.apiClient = apiClient;
        this.mapper = mapper;
        this.ingestService = ingestService;
        this.sessionRepository = sessionRepository;
    }

    @Async
    public void handleAsync(NolioWebhookPayload payload) {
        try {
            handle(payload);
        } catch (RuntimeException e) {
            log.warn("Nolio real-event webhook {} {} failed: {}",
                    payload.notifType(), payload.objectId(), e.getMessage());
        }
    }

    private void handle(NolioWebhookPayload payload) {
        if (!"Training".equals(payload.objectType())) {
            log.debug("Ignoring Nolio real event for unsupported object_type {}", payload.objectType());
            return;
        }
        User user = userRepository.findByNolioUserId(String.valueOf(payload.userId())).orElse(null);
        if (user == null || user.getNolioAccessToken() == null) {
            log.info("Nolio real event for unknown/unlinked nolio user {} — skipped", payload.userId());
            return;
        }

        String nolioActivityId = String.valueOf(payload.objectId());
        switch (payload.notifType()) {
            case "new_event", "updated_event" -> upsert(user, payload.objectId(), nolioActivityId);
            case "deleted_event" -> delete(user, nolioActivityId);
            default -> log.debug("Ignoring Nolio real event notif_type {}", payload.notifType());
        }
    }

    private void upsert(User user, long nolioId, String nolioActivityId) {
        JsonNode workout = apiClient.getTraining(user, nolioId);
        if (workout == null) {
            log.warn("Nolio training {} not found when handling webhook for user {}", nolioId, user.getId());
            return;
        }
        CompletedSession mapped = mapper.map(workout);
        Optional<CompletedSession> existing = sessionRepository
                .findByUserIdAndNolioActivityId(user.getId(), nolioActivityId);
        if (existing.isPresent()) {
            CompletedSession session = existing.get();
            mapper.applyUpdate(session, mapped);
            sessionRepository.save(session);
            log.info("Refreshed session {} from Nolio training {} for user {}", session.getId(), nolioId, user.getId());
        } else {
            ingestService.ingest(user, mapped);
        }
    }

    /** A session merged with a Strava import only loses its Nolio link; pure Nolio sessions are removed. */
    private void delete(User user, String nolioActivityId) {
        sessionRepository.findByUserIdAndNolioActivityId(user.getId(), nolioActivityId).ifPresent(session -> {
            if (session.getStravaActivityId() != null) {
                session.setNolioActivityId(null);
                sessionRepository.save(session);
                log.info("Unlinked Nolio activity {} from Strava-backed session {}", nolioActivityId, session.getId());
            } else {
                sessionRepository.delete(session);
                log.info("Deleted session {} after Nolio training {} was removed", session.getId(), nolioActivityId);
            }
        });
    }
}
