package com.koval.trainingplannerbackend.integration.suunto;

import com.koval.trainingplannerbackend.auth.User;
import com.koval.trainingplannerbackend.auth.UserRepository;
import com.koval.trainingplannerbackend.config.exceptions.ResourceNotFoundException;
import com.koval.trainingplannerbackend.training.history.CompletedSession;
import com.koval.trainingplannerbackend.training.history.CompletedSessionRepository;
import com.koval.trainingplannerbackend.training.history.SessionFitFileService;
import com.koval.trainingplannerbackend.training.history.SessionService;
import com.koval.trainingplannerbackend.training.history.fit.FitFileStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Imports completed Suunto workouts into CompletedSession storage. Summaries come from the
 * workout list API; the full FIT file is downloaded per workout so power/HR/GPS detail feeds
 * the regular FIT-based metrics (power curve, NP, normalized speed).
 *
 * <p>Two entry points: {@link #importHistory} (manual backfill from the connected-apps modal)
 * and {@link #importSingleWorkout} (webhook-triggered, one workout per notification).
 */
@Service
public class SuuntoActivitySyncService {

    private static final Logger log = LoggerFactory.getLogger(SuuntoActivitySyncService.class);
    private static final int HISTORY_LOOKBACK_DAYS = 30;

    private final SuuntoOAuthService oauthService;
    private final SuuntoApiClient apiClient;
    private final SuuntoActivityMapper mapper = new SuuntoActivityMapper();
    private final CompletedSessionRepository sessionRepository;
    private final SessionService sessionService;
    private final SessionFitFileService fitFileService;
    private final UserRepository userRepository;
    private final FitFileStore fitFileStore;

    public SuuntoActivitySyncService(SuuntoOAuthService oauthService,
                                     SuuntoApiClient apiClient,
                                     CompletedSessionRepository sessionRepository,
                                     SessionService sessionService,
                                     SessionFitFileService fitFileService,
                                     UserRepository userRepository,
                                     FitFileStore fitFileStore) {
        this.oauthService = oauthService;
        this.apiClient = apiClient;
        this.sessionRepository = sessionRepository;
        this.sessionService = sessionService;
        this.fitFileService = fitFileService;
        this.userRepository = userRepository;
        this.fitFileStore = fitFileStore;
    }

    /**
     * Manual history import: pulls workouts synced in the last {@value #HISTORY_LOOKBACK_DAYS}
     * days. Idempotent — dedupes on the Suunto workout key, so re-clicks only import what's new.
     */
    public SyncResult importHistory(String userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        if (user.getSuuntoUserId() == null || user.getSuuntoAccessToken() == null) {
            throw new IllegalStateException("Suunto is not connected for this user");
        }

        String accessToken = oauthService.ensureValidToken(user);
        long sinceMillis = Instant.now().minus(HISTORY_LOOKBACK_DAYS, ChronoUnit.DAYS).toEpochMilli();

        List<Map<String, Object>> workouts = apiClient.listWorkouts(accessToken, sinceMillis);

        Set<String> existingIds = existingSuuntoIds(userId);

        int skippedDuplicates = 0;
        int skippedErrors = 0;
        int imported = 0;

        for (Map<String, Object> workout : workouts) {
            String workoutKey = String.valueOf(workout.get("workoutKey"));
            if (existingIds.contains(workoutKey)) {
                skippedDuplicates++;
                continue;
            }
            try {
                importOne(user, accessToken, workout, false);
                imported++;
            } catch (RuntimeException e) {
                log.warn("Failed to import Suunto workout {}: {}", workoutKey, e.getMessage());
                skippedErrors++;
            }
        }

        user.setSuuntoLastSyncAt(LocalDateTime.now());
        userRepository.save(user);

        return new SyncResult(workouts.size(), imported, skippedDuplicates, skippedErrors);
    }

    /**
     * Import a single workout (called by the webhook). Skips if already imported.
     * Notifies the user — mirrors the Strava webhook behaviour.
     */
    public void importSingleWorkout(User user, String workoutKey) {
        if (existingSuuntoIds(user.getId()).contains(workoutKey)) {
            log.debug("Suunto workout {} already imported, skipping", workoutKey);
            return;
        }

        String accessToken = oauthService.ensureValidToken(user);
        Map<String, Object> workout = apiClient.fetchWorkout(accessToken, workoutKey);
        if (workout.isEmpty()) {
            log.warn("Suunto workout {} returned empty response", workoutKey);
            return;
        }
        // The webhook payload carries the id but the summary may not echo it — make sure it's set.
        if (workout.get("workoutKey") == null) {
            workout = new java.util.LinkedHashMap<>(workout);
            workout.put("workoutKey", workoutKey);
        }

        importOne(user, accessToken, workout, true);
        log.info("Imported Suunto workout {} for user {}", workoutKey, user.getId());
    }

    /** Map → save → attach FIT (non-fatal). */
    private void importOne(User user, String accessToken, Map<String, Object> workout, boolean notifyUser) {
        CompletedSession session = mapper.map(workout);
        CompletedSession saved = sessionService.saveSession(session, user.getId(), notifyUser);

        try {
            Optional<byte[]> fitBytes = apiClient.exportFit(accessToken, saved.getSuuntoActivityId());
            if (fitBytes.isPresent()) {
                fitFileStore.store(saved, fitBytes.get());
                fitFileService.recomputeMetricsAfterFitChange(saved);
            }
        } catch (RuntimeException fitEx) {
            log.warn("Failed to store FIT for Suunto workout {}: {}",
                    saved.getSuuntoActivityId(), fitEx.getMessage());
        }
    }

    private Set<String> existingSuuntoIds(String userId) {
        return sessionRepository.findSuuntoActivityIdsByUserId(userId)
                .stream()
                .map(CompletedSession::getSuuntoActivityId)
                .collect(Collectors.toSet());
    }

    public record SyncResult(int totalFetched, int newlyImported, int skippedDuplicates, int skippedErrors) {}
}
