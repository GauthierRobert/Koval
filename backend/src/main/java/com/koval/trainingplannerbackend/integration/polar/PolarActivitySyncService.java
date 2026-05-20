package com.koval.trainingplannerbackend.integration.polar;

import com.koval.trainingplannerbackend.auth.User;
import com.koval.trainingplannerbackend.auth.UserRepository;
import com.koval.trainingplannerbackend.config.exceptions.ResourceNotFoundException;
import com.koval.trainingplannerbackend.training.history.CompletedSession;
import com.koval.trainingplannerbackend.training.history.CompletedSessionRepository;
import com.koval.trainingplannerbackend.training.history.SessionService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Imports completed Polar exercises into CompletedSession storage using AccessLink's
 * transaction protocol.
 */
@Service
public class PolarActivitySyncService {

    private static final Logger log = LoggerFactory.getLogger(PolarActivitySyncService.class);

    private final PolarOAuthService oauthService;
    private final PolarApiClient apiClient;
    private final PolarActivityMapper mapper = new PolarActivityMapper();
    private final CompletedSessionRepository sessionRepository;
    private final SessionService sessionService;
    private final UserRepository userRepository;

    public PolarActivitySyncService(PolarOAuthService oauthService,
                                     PolarApiClient apiClient,
                                     CompletedSessionRepository sessionRepository,
                                     SessionService sessionService,
                                     UserRepository userRepository) {
        this.oauthService = oauthService;
        this.apiClient = apiClient;
        this.sessionRepository = sessionRepository;
        this.sessionService = sessionService;
        this.userRepository = userRepository;
    }

    /**
     * Pulls all new exercises available in a fresh transaction. Idempotent against
     * sessions we've already imported (deduped on Polar exercise id).
     */
    public SyncResult importNewExercises(String userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        if (user.getPolarUserId() == null || user.getPolarAccessToken() == null) {
            throw new IllegalStateException("Polar is not connected for this user");
        }

        String accessToken = oauthService.ensureValidToken(user);
        String polarUserId = user.getPolarUserId();

        var txOpt = apiClient.openExerciseTransaction(accessToken, polarUserId);
        if (txOpt.isEmpty()) {
            log.debug("No new Polar exercises for user {}", userId);
            user.setPolarLastSyncAt(LocalDateTime.now());
            userRepository.save(user);
            return new SyncResult(0, 0, 0, 0);
        }
        String txId = txOpt.get();

        List<String> urls = apiClient.listExercises(accessToken, polarUserId, txId);

        Set<String> existingIds = sessionRepository.findPolarActivityIdsByUserId(userId)
                .stream()
                .map(CompletedSession::getPolarActivityId)
                .collect(Collectors.toSet());

        int skippedDuplicates = 0;
        int skippedErrors = 0;
        List<CompletedSession> imported = new ArrayList<>();

        for (String url : urls) {
            Map<String, Object> exercise = apiClient.fetchExercise(accessToken, url);
            if (exercise.isEmpty()) {
                skippedErrors++;
                continue;
            }
            String polarId = String.valueOf(exercise.get("id"));
            if (existingIds.contains(polarId)) {
                skippedDuplicates++;
                continue;
            }
            try {
                CompletedSession mapped = mapper.map(exercise);
                imported.add(sessionService.saveSession(mapped, userId));
            } catch (RuntimeException e) {
                log.warn("Failed to import Polar exercise {}: {}", polarId, e.getMessage());
                skippedErrors++;
            }
        }

        apiClient.commitExerciseTransaction(accessToken, polarUserId, txId);
        user.setPolarLastSyncAt(LocalDateTime.now());
        userRepository.save(user);

        return new SyncResult(urls.size(), imported.size(), skippedDuplicates, skippedErrors);
    }

    public record SyncResult(int totalFetched, int newlyImported, int skippedDuplicates, int skippedErrors) {}
}
