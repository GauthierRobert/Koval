package com.koval.trainingplannerbackend.integration.zwift;

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

@Service
public class ZwiftActivitySyncService {

    private static final Logger log = LoggerFactory.getLogger(ZwiftActivitySyncService.class);

    private final ZwiftApiClient zwiftApiClient;
    private final ZwiftActivityMapper mapper = new ZwiftActivityMapper();
    private final CompletedSessionRepository sessionRepository;
    private final SessionService sessionService;
    private final UserRepository userRepository;

    public ZwiftActivitySyncService(ZwiftApiClient zwiftApiClient,
                                     CompletedSessionRepository sessionRepository,
                                     SessionService sessionService,
                                     UserRepository userRepository) {
        this.zwiftApiClient = zwiftApiClient;
        this.sessionRepository = sessionRepository;
        this.sessionService = sessionService;
        this.userRepository = userRepository;
    }

    public SyncResult importHistory(String userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        if (user.getZwiftAccessToken() == null || user.getZwiftAccessToken().isBlank()) {
            throw new IllegalStateException("Zwift is not connected for this user");
        }

        List<Map<String, Object>> activities = zwiftApiClient.fetchActivities(user, 50);

        Set<String> existingIds = sessionRepository.findZwiftActivityIdsByUserId(userId)
                .stream()
                .map(CompletedSession::getZwiftActivityId)
                .collect(Collectors.toSet());

        int skippedDuplicates = 0;
        int skippedErrors = 0;
        List<CompletedSession> importedSessions = new ArrayList<>();

        for (Map<String, Object> activity : activities) {
            String zwiftId = String.valueOf(activity.get("id"));

            if (existingIds.contains(zwiftId)) {
                skippedDuplicates++;
                continue;
            }

            try {
                CompletedSession session = mapper.map(activity);
                CompletedSession saved = sessionService.saveSession(session, userId);
                importedSessions.add(saved);
            } catch (Exception e) {
                log.warn("Failed to import Zwift activity {}: {}", zwiftId, e.getMessage());
                skippedErrors++;
            }
        }

        user.setZwiftLastSyncAt(LocalDateTime.now());
        userRepository.save(user);

        return new SyncResult(activities.size(), importedSessions.size(), skippedDuplicates, skippedErrors);
    }

    public record SyncResult(int totalFetched, int newlyImported, int skippedDuplicates, int skippedErrors) {}
}
